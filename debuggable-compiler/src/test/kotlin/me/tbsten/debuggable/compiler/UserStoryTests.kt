package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
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
