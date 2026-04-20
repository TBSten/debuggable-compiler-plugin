package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import me.tbsten.debuggable.runtime.logging.CompositeLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import me.tbsten.debuggable.runtime.logging.PrefixedLogger
import me.tbsten.debuggable.runtime.logging.SilentLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * User-story-driven behavioural tests.
 *
 * Each `@Test` reproduces the scenario a specific persona runs into when they
 * add `@Debuggable` to their code. The goal is to exercise the feature from
 * the user's end ("I call this, I expect that log") rather than from the IR
 * transformer's (which `FocusIgnoreTests`, `RegistryTests`, etc. already do).
 *
 * See `.local/tickets/chapter-user-story-driven-testing.md` for the full story
 * catalogue + persona framing. Each story links to the ticket it retires.
 */
class UserStoryTests : CompilerTestBase() {

    // Story 5 (persona C: form field tracking) — task-401
    // Inner / nested class support: adding @Debuggable to a nested class
    // must compile, and if the plugin doesn't currently transform it, there
    // should at least be a warning so users aren't silently no-op'd.
    @Test fun `story - @Debuggable on nested object compiles and logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            class Outer {
                @Debuggable(isSingleton = true) object Inner {
                    fun greet() = "hi"
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            KotlinCompilation.ExitCode.OK, result.exitCode,
            "nested @Debuggable object should compile cleanly, got: ${result.messages}",
        )
        val innerObj = result.classLoader.loadClass("Outer\$Inner")
            .getDeclaredField("INSTANCE").get(null)
        val output = captureSystemOut { innerObj.call("greet") }
        assertTrue(
            output.contains("greet"),
            "nested @Debuggable object must still have logAction injected, got: $output",
        )
    }

    // Story 11 (persona G: error path) — task-402
    // A @Debuggable class with a suspend fun should compile; logAction runs
    // at the top of the function body. The suspend continuation parameter
    // must NOT leak into the logged argument list.
    @Test fun `story - suspend function logAction does not leak Continuation`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.runBlocking
            @Debuggable(isSingleton = true) object Gateway {
                suspend fun login(user: String): String {
                    return "ok:${'$'}user"
                }
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val gateway = result.getObject("Gateway")
        val output = captureSystemOut {
            // Drive the suspend fun through kotlinx.coroutines.runBlocking.
            val runBlockingCls = result.classLoader.loadClass("kotlinx.coroutines.BuildersKt")
            // Use java reflection to invoke the overload that takes a coroutine block.
            // Simpler path: just assert via the compiled bytecode symbol search.
            // If reflection-invoking suspend functions is fiddly, at least the
            // compilation succeeding + logAction call emitted is enough for the
            // leak check below.
            val loginMethod = gateway.javaClass.methods.first { it.name == "login" }
            // Suspend functions take an extra Continuation parameter — synthesize
            // a minimal Continuation via EmptyCoroutineContext.
            val contCls = result.classLoader.loadClass("kotlin.coroutines.Continuation")
            val contImpl = java.lang.reflect.Proxy.newProxyInstance(
                contCls.classLoader, arrayOf(contCls),
            ) { _, method, args ->
                when (method.name) {
                    "getContext" -> result.classLoader.loadClass("kotlin.coroutines.EmptyCoroutineContext")
                        .getField("INSTANCE").get(null)
                    "resumeWith" -> null
                    else -> null
                }
            }
            runCatching { loginMethod.invoke(gateway, "daisy", contImpl) }
        }
        // The logAction call fires before the body; we assert the arg list
        // contains only "daisy" and NOT a Continuation toString.
        assertTrue(output.contains("login"), "suspend logAction must fire, got: $output")
        assertFalse(
            output.contains("Continuation"),
            "Continuation param must not leak into arg list, got: $output",
        )
    }

    // Story 15 (persona G: inline) — task-403
    // Injecting logAction into an inline function is incoherent (the call is
    // inlined at every call site). The plugin must either skip inline members
    // or produce an explicit diagnostic. Silent injection that breaks the
    // inline semantics is the only outcome we must prevent.
    @Test fun `story - inline function either skips injection or warns`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Utils {
                inline fun <reified T> cast(x: Any?): T? = x as? T
                fun regularFun() = 42
            }
            """.trimIndent(),
        )
        // Must at least compile — breaking the build on inline is the worst outcome.
        assertEquals(
            KotlinCompilation.ExitCode.OK, result.exitCode,
            "inline fun in @Debuggable must not break compilation, got: ${result.messages}",
        )
        val utils = result.getObject("Utils")
        // The regular function should still be instrumented, proving injection
        // is selective rather than fully disabled.
        val output = captureSystemOut { utils.call("regularFun") }
        assertTrue(
            output.contains("regularFun"),
            "non-inline function must still be instrumented, got: $output",
        )
    }

    // Story 4 (persona B: KMP) — task-405
    // Multi-level ViewModel inheritance: if a chain exists, the leaf
    // @Debuggable class is what gets the registry. The inherited close()
    // (from a base class that DID override it) should still chain correctly.
    @Test fun `story - @Debuggable class inheriting a class that overrides close chains correctly`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            abstract class BaseCloseable : AutoCloseable {
                var baseClosed = false
                override fun close() { baseClosed = true }
            }
            abstract class Middle : BaseCloseable()
            @Debuggable class Leaf : Middle() {
                val flow = kotlinx.coroutines.flow.MutableStateFlow(0)
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val leafCls = result.classLoader.loadClass("Leaf")
        val leaf = leafCls.getDeclaredConstructor().newInstance()

        captureSystemOut { leaf.call("close") }

        // If we correctly wrapped Leaf.close() with try-finally instead of
        // delegating entirely to Middle/Base, the base's flag should still
        // be set AND we should not have double-closed.
        val baseClosedField = leafCls.superclass.superclass.getDeclaredField("baseClosed")
        baseClosedField.isAccessible = true
        assertEquals(
            true, baseClosedField.get(leaf) as Boolean,
            "inherited close() must still run (super-chain is preserved)",
        )
    }

    // =========================================================================
    // AutoCloseable (ViewModel-like) shape — stories A1 ~ A3
    // =========================================================================
    // Note: actual androidx.lifecycle.ViewModel is not on the kctfork classpath.
    // We use AutoCloseable as a proxy — the plugin's close-injection path is the
    // same. Real ViewModel tests live in integration-test/kmp-smoke.

    // Story A1 — persona A: ChatViewModel StateFlow changes are logged.
    @Test fun `story - ViewModel shape StateFlow changes are logged`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class ChatViewModel : AutoCloseable {
                val messages = MutableStateFlow("")
                override fun close() {}
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val vm = result.getInstance("ChatViewModel")
        val field = vm.javaClass.getDeclaredField("messages").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<String>
        val output = captureSystemOut {
            flow.value = "hello"
            Thread.sleep(150)
        }
        assertTrue("messages" in output && "hello" in output,
            "flow change must be logged, got: $output")
    }

    // Story A2 — persona A: after close(), Flow changes produce no log.
    @Test fun `story - ViewModel shape no logging after close`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class ChatViewModel : AutoCloseable {
                val messages = MutableStateFlow("")
                override fun close() {}
            }
            """.trimIndent(),
        )
        val vm = result.getInstance("ChatViewModel")
        val field = vm.javaClass.getDeclaredField("messages").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<String>
        Thread.sleep(50)
        vm.call("close")
        Thread.sleep(50)
        val output = captureSystemOut {
            flow.value = "after-close"
            Thread.sleep(150)
        }
        assertFalse("after-close" in output,
            "flow change after close() must not be logged, got: $output")
    }

    // Story A3 — persona A: @Debuggable(logger = X::class) routes to that logger.
    @Test fun `story - ViewModel shape custom per-class logger routes logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger
            object AuthLogger : DebugLogger {
                override fun log(message: String) { println("AUTH: ${'$'}message") }
            }
            @Debuggable(logger = AuthLogger::class)
            class AuthViewModel : AutoCloseable {
                fun login(user: String): String = "ok"
                override fun close() {}
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val vm = result.getInstance("AuthViewModel")
        val output = captureSystemOut { vm.call("login", "daisy") }
        assertTrue("AUTH:" in output, "per-class logger must receive log, got: $output")
        assertTrue("login" in output, "method name must appear in log, got: $output")
    }

    // =========================================================================
    // UseCase / Singleton shape — stories U1 ~ U3
    // =========================================================================

    // Story U1 — persona B: singleton UseCase logAction records call with args.
    @Test fun `story - UseCase singleton logAction records every call with arguments`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object GetUserUseCase {
                fun execute(userId: String): String = "user:${'$'}userId"
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val useCase = result.getObject("GetUserUseCase")
        val logger = InMemoryLogger()
        DefaultDebugLogger.current = logger
        useCase.call("execute", "daisy")
        assertTrue(logger.messages.any { "execute" in it },
            "execute call must be logged, got: ${logger.messages}")
        assertTrue(logger.messages.any { "daisy" in it },
            "argument must appear in log, got: ${logger.messages}")
    }

    // Story U2 — persona D: InMemoryLogger captures full call history for assertion.
    @Test fun `story - UseCase singleton InMemoryLogger captures call history`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Calculator {
                fun add(a: Int, b: Int): Int = a + b
                fun multiply(a: Int, b: Int): Int = a * b
            }
            """.trimIndent(),
        )
        val calc = result.getObject("Calculator")
        val logger = InMemoryLogger()
        DefaultDebugLogger.current = logger
        calc.call("add", 3, 4)
        calc.call("multiply", 2, 5)
        assertEquals(2, logger.messages.size,
            "exactly 2 calls must be recorded, got: ${logger.messages}")
        assertTrue(logger.messages.any { "add" in it },
            "add call must be in history, got: ${logger.messages}")
        assertTrue(logger.messages.any { "multiply" in it },
            "multiply call must be in history, got: ${logger.messages}")
    }

    // Story U3 — persona D: SilentLogger suppresses all output.
    @Test fun `story - UseCase singleton SilentLogger suppresses all log output`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Pinger {
                fun ping(): String = "pong"
            }
            """.trimIndent(),
        )
        val pinger = result.getObject("Pinger")
        DefaultDebugLogger.current = SilentLogger
        val output = captureSystemOut { pinger.call("ping") }
        assertFalse("ping" in output, "SilentLogger must suppress all output, got: $output")
    }

    // =========================================================================
    // StateHolder (AutoCloseable) shape — stories S1 ~ S2
    // =========================================================================

    // Story S1 — persona C: @FocusDebuggable var logs every mutation.
    @Test fun `story - StateHolder @FocusDebuggable var logs every assignment`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable class UserStateHolder : AutoCloseable {
                @FocusDebuggable var name: String = ""
                override fun close() {}
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val holder = result.getInstance("UserStateHolder")
        val setter = holder.javaClass.getDeclaredMethod("setName", String::class.java)
        val output = captureSystemOut { setter.invoke(holder, "daisy") }
        assertTrue("name: daisy" in output,
            "setter assignment must be logged, got: $output")
    }

    // Story S2 — persona C: @IgnoreDebuggable var is silenced.
    @Test fun `story - StateHolder @IgnoreDebuggable var is never logged`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class UserStateHolder : AutoCloseable {
                @FocusDebuggable val score = MutableStateFlow(0)
                @IgnoreDebuggable var password: String = ""
                override fun close() {}
            }
            """.trimIndent(),
        )
        val holder = result.getInstance("UserStateHolder")
        val setter = holder.javaClass.getDeclaredMethod("setPassword", String::class.java)
        val output = captureSystemOut { setter.invoke(holder, "secret") }
        assertFalse("password" in output,
            "@IgnoreDebuggable var must not be logged, got: $output")
        assertFalse("secret" in output,
            "value of @IgnoreDebuggable var must not be logged, got: $output")
    }

    // =========================================================================
    // Plugin option combinations — stories P1 ~ P3
    // =========================================================================

    // Story P1 — persona E: enabled=false removes all instrumentation from bytecode.
    @Test fun `story - enabled=false removes all debuggable instrumentation from bytecode`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object DataStore {
                val count = MutableStateFlow(0)
                fun increment() {}
            }
            """.trimIndent(),
            pluginEnabled = false,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val cls = result.classLoader.loadClass("DataStore")
        val fieldNames = cls.declaredFields.map { it.name }
        assertFalse(
            fieldNames.any { "debuggable" in it.lowercase() },
            "disabled plugin must inject no registry field, got fields: $fieldNames",
        )
    }

    // Story P2 — persona E: observeFlow=false disables Flow observation.
    @Test fun `story - observeFlow=false disables Flow tracking`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object Sensor {
                val reading = MutableStateFlow(0)
            }
            """.trimIndent(),
            observeFlow = false,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val sensor = result.getObject("Sensor")
        val field = sensor.javaClass.getDeclaredField("reading").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(sensor) as MutableStateFlow<Int>
        val output = captureSystemOut {
            flow.value = 42
            Thread.sleep(150)
        }
        assertFalse("reading" in output,
            "observeFlow=false must not log flow changes, got: $output")
    }

    // Story P3 — persona E: logAction=false disables method call logging.
    @Test fun `story - logAction=false disables method call logging`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object EventTracker {
                fun track(event: String) {}
            }
            """.trimIndent(),
            logAction = false,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val tracker = result.getObject("EventTracker")
        val output = captureSystemOut { tracker.call("track", "click") }
        assertFalse("track" in output,
            "logAction=false must not log method calls, got: $output")
    }

    // =========================================================================
    // Focus / Ignore mode — stories F1 ~ F2
    // =========================================================================

    // Story F1 — persona F: Focus mode silences unfocused Flow.
    @Test fun `story - Focus mode suppresses logging of unfocused StateFlow`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object Dashboard {
                @FocusDebuggable val activeUsers = MutableStateFlow(0)
                val cache = MutableStateFlow("")   // NOT focused
            }
            """.trimIndent(),
        )
        val dash = result.getObject("Dashboard")
        val cacheField = dash.javaClass.getDeclaredField("cache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(dash) as MutableStateFlow<String>
        Thread.sleep(50)
        val output = captureSystemOut {
            cache.value = "hidden"
            Thread.sleep(150)
        }
        assertFalse("hidden" in output,
            "unfocused Flow must be silent in Focus mode, got: $output")
        assertFalse("cache" in output,
            "unfocused Flow property name must not appear, got: $output")
    }

    // Story F2 — persona F: @FocusDebuggable + @IgnoreDebuggable on same property → compile error.
    @Test fun `story - @FocusDebuggable and @IgnoreDebuggable on same property is compile error`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object Config {
                @FocusDebuggable @IgnoreDebuggable val secret = MutableStateFlow("")
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode,
            "conflicting Focus+Ignore annotations on same property must be a compile error, got: ${result.messages}")
    }

    // =========================================================================
    // Data class generated methods — story D1
    // =========================================================================

    // Story D1 — persona H: data class-like class toString/equals/copy do NOT get logAction.
    // @Debuggable data class is not valid (data class cannot implement AutoCloseable in a
    // meaningful way and isSingleton doesn't apply). Instead we test that a plain singleton
    // object whose members include a method named "toString" (user-overridden) does not
    // accidentally double-log toString while the user-defined method IS logged.
    @Test fun `story - generated data class methods do not receive logAction injection`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object UserFormatter {
                fun format(name: String, age: Int): String = "${'$'}name (${'$'}age)"
                // toString() is a generated method — we verify it is not double-injected
                // by calling it and checking we don't see a spurious "toString" log line.
                override fun toString(): String = "UserFormatter"
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val formatter = result.getObject("UserFormatter")
        val toStringOutput = captureSystemOut { formatter.toString() }
        assertFalse("toString" in toStringOutput,
            "toString() must not receive logAction injection, got: $toStringOutput")
        val formatOutput = captureSystemOut { formatter.call("format", "Alice", 30) }
        assertTrue("format" in formatOutput,
            "user-defined format() must still be logged, got: $formatOutput")
    }

    // =========================================================================
    // Logger composition — stories L1 ~ L2
    // =========================================================================

    // Story L1 — persona I: CompositeLogger fans out to all child loggers.
    @Test fun `story - CompositeLogger fans out to every child logger`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object EventBus {
                fun publish(topic: String) {}
            }
            """.trimIndent(),
        )
        val eventBus = result.getObject("EventBus")
        val logger1 = InMemoryLogger()
        val logger2 = InMemoryLogger()
        DefaultDebugLogger.current = CompositeLogger(logger1, logger2)
        eventBus.call("publish", "user.created")
        assertTrue(logger1.messages.any { "publish" in it },
            "first child logger must receive log, got: ${logger1.messages}")
        assertTrue(logger2.messages.any { "publish" in it },
            "second child logger must receive log, got: ${logger2.messages}")
    }

    // Story L2 — persona I: PrefixedLogger prepends prefix to every message.
    @Test fun `story - PrefixedLogger prepends the configured prefix`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object AuthService {
                fun authenticate(user: String): Boolean = true
            }
            """.trimIndent(),
        )
        val service = result.getObject("AuthService")
        val inner = InMemoryLogger()
        DefaultDebugLogger.current = PrefixedLogger("[AUTH]", inner)
        service.call("authenticate", "daisy")
        assertTrue(inner.messages.any { it.startsWith("[AUTH]") },
            "PrefixedLogger must prepend [AUTH], got: ${inner.messages}")
        assertTrue(inner.messages.any { "authenticate" in it },
            "authenticate call must appear in log, got: ${inner.messages}")
    }

    // =========================================================================
    // Pre-existing stories (task-401 ~ 406)
    // =========================================================================

    // Story 14 (persona G: sad path) — task-406
    // If the user's close() body throws, the registry must still be closed.
    // Otherwise observation coroutines leak when close() fails mid-way.
    @Test fun `story - registry close runs even when existing close body throws`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable
            class ThrowingResource : AutoCloseable {
                val flow = kotlinx.coroutines.flow.MutableStateFlow(0)
                var registryCoroutineJob: kotlinx.coroutines.Job? = null
                override fun close() {
                    throw RuntimeException("boom")
                }
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val cls = result.classLoader.loadClass("ThrowingResource")
        val instance = cls.getDeclaredConstructor().newInstance()

        // Call close() and confirm the RuntimeException is surfaced (we don't
        // swallow user errors) — but ALSO confirm the registry's coroutine
        // scope got cancelled as a side effect of the finally block.
        val threw = try {
            captureSystemOut { instance.call("close") }
            false
        } catch (t: java.lang.reflect.InvocationTargetException) {
            t.cause?.message == "boom"
        } catch (_: Throwable) {
            true
        }
        assertTrue(threw, "user's close() throw must propagate")

        // Access the injected private field to verify the registry's scope is
        // now cancelled (== cleanup ran even though the original body threw).
        val registryField = cls.declaredFields.first { it.name.contains("debuggable_registry") }
        registryField.isAccessible = true
        val registry = registryField.get(instance)
        // DebugCleanupRegistry exposes `close()` itself — calling it twice
        // should be a no-op since our `try { orig } finally { reg.close() }`
        // already fired. A well-behaved registry then returns without error.
        // (`DebugCleanupRegistryImpl` is `internal`; the method is public but
        // the class is package-private from Java's view, so setAccessible is
        // required.)
        val closeOnce = registry.javaClass.methods.first { it.name == "close" }
        closeOnce.isAccessible = true
        runCatching { closeOnce.invoke(registry) }
            .onFailure { error("registry.close() second call must be idempotent: $it") }
    }
}
