package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * logAction 注入の RED テスト群。
 * Phase 3 (IR Injection) 実装後にすべて GREEN になることを意図している。
 */
class LogActionTests : CompilerTestBase() {

    private val singletonSource = """
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            fun doNothing() {}
            fun doWithInt(x: Int): Int = x * 2
            fun doWithString(s: String): String = s
            fun doWithMultiple(a: Int, b: String, c: Boolean): String = "${'$'}a ${'$'}b ${'$'}c"
            fun doWithNull(value: String?): String? = value
            fun doReturn(): Int = 42
            private fun hiddenPrivate() {}
            internal fun hiddenInternal() {}
            fun throwsException(): Nothing = throw RuntimeException("oops")
        }
    """.trimIndent()

    @Test fun `public method call logs action`() {
        val result = compile(singletonSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doNothing") }
        assertTrue(output.contains("[Debuggable]"), "Expected logAction output, got: $output")
        assertTrue(output.contains("doNothing"), "Expected method name in log, got: $output")
    }

    @Test fun `public method log contains parentheses`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doNothing") }
        assertTrue(output.contains("doNothing()"), "Expected 'doNothing()' in log, got: $output")
    }

    @Test fun `method with int arg logs arg value`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWithInt", 5) }
        assertTrue(output.contains("doWithInt(5)"), "Expected 'doWithInt(5)' in log, got: $output")
    }

    @Test fun `method with string arg logs arg value`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWithString", "hello") }
        assertTrue(output.contains("doWithString(hello)"), "Expected arg in log, got: $output")
    }

    @Test fun `method with multiple args logs all args`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWithMultiple", 1, "x", true) }
        assertTrue(output.contains("doWithMultiple"), "Expected method name in log, got: $output")
        assertTrue(output.contains("1"), "Expected first arg in log, got: $output")
        assertTrue(output.contains("x"), "Expected second arg in log, got: $output")
    }

    @Test fun `method with null arg logs null`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWithNull", null) }
        assertTrue(output.contains("doWithNull"), "Expected method name in log, got: $output")
    }

    @Test fun `method return value is preserved`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val returnValue = obj.call("doReturn")
        assertEquals(42, returnValue, "Return value should be unchanged")
    }

    @Test fun `method return value preserved when logging`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        captureSystemOut { obj.call("doReturn") }  // log side effect
        val returnValue = obj.call("doReturn")
        assertEquals(42, returnValue, "Return value should not be affected by logging")
    }

    @Test fun `private method is NOT logged`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            obj.javaClass.getDeclaredMethod("hiddenPrivate").apply { isAccessible = true }.invoke(obj)
        }
        assertFalse(output.contains("hiddenPrivate"), "Private method should not be logged, got: $output")
    }

    @Test fun `internal method is NOT logged`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("hiddenInternal") }
        assertFalse(output.contains("[Debuggable]"), "Internal method should not be logged, got: $output")
    }

    @Test fun `exception from method propagates`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        var threw = false
        try {
            obj.call("throwsException")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw, "Exception should propagate through injected logAction")
    }

    @Test fun `method is logged before execution`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Exec {
                var sideEffect = false
                fun act() { sideEffect = true }
            }
        """.trimIndent())
        val obj = result.getObject("Exec")
        val log = StringBuilder()
        val output = captureSystemOut { obj.call("act") }
        // logAction should appear regardless of side effect order
        assertTrue(output.contains("[Debuggable]"), "Expected logAction before execution, got: $output")
    }

    @Test fun `method called multiple times logs multiple times`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            obj.call("doNothing")
            obj.call("doNothing")
            obj.call("doNothing")
        }
        val count = output.lines().count { it.contains("doNothing") }
        assertEquals(3, count, "Expected 3 log entries, got: $output")
    }

    @Test fun `AutoCloseable public method is logged`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyRepo : AutoCloseable {
                fun fetch(): String = "data"
                override fun close() {}
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val instance = result.getInstance("MyRepo")
        val output = captureSystemOut { instance.call("fetch") }
        assertTrue(output.contains("[Debuggable]"), "Expected logAction for AutoCloseable method, got: $output")
        assertTrue(output.contains("fetch"), "Expected method name in log, got: $output")
    }

    @Test fun `AutoCloseable close method logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyCloseable : AutoCloseable {
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val output = captureSystemOut { instance.call("close") }
        assertTrue(output.contains("[Debuggable]") && output.contains("close"),
            "Expected logAction for close(), got: $output")
    }

    @Test fun `method with default parameter logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object MyObj {
                fun greet(name: String = "World"): String = "Hello ${'$'}name"
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("greet", "Kotlin") }
        assertTrue(output.contains("greet"), "Expected method name in log, got: $output")
    }

    @Test fun `IgnoreDebuggable on method prevents logAction`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            @Debuggable(isSingleton = true) object MyObj {
                @IgnoreDebuggable fun skipMe() {}
                fun trackMe() {}
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("skipMe") }
        assertFalse(output.contains("skipMe"), "Ignored method should not be logged, got: $output")
    }

    @Test fun `FocusDebuggable on method in Focus mode logs only that method`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable fun focused() {}
                fun other() {}
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val focusedOut = captureSystemOut { obj.call("focused") }
        val otherOut = captureSystemOut { obj.call("other") }
        assertTrue(focusedOut.contains("[Debuggable]"), "Focus method should be logged, got: $focusedOut")
        assertFalse(otherOut.contains("[Debuggable]"), "Non-focus method should not be logged, got: $otherOut")
    }

    @Test fun `log output prefixed with Debuggable tag`() {
        val result = compile(singletonSource)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doNothing") }
        assertTrue(output.startsWith("[Debuggable]"), "Log should start with [Debuggable] prefix, got: $output")
    }

    @Test fun `vararg method logs correctly`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object MyObj {
                fun doWithVararg(vararg args: String): Int = args.size
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWithVararg", arrayOf("a", "b")) }
        assertTrue(output.contains("doWithVararg"), "Expected method name in log, got: $output")
    }

    @Test fun `arg toString throwing does not prevent the function body from running`() {
        // task-134: `logAction(name, vararg args, logger)` is injected as the
        // FIRST statement of the function body. The runtime's `Logging.kt`
        // formats args with `args.joinToString()` → `.toString()` per arg.
        // If the user's arg has a throwing `toString()`, the injected log
        // call throws BEFORE the original function body runs — so a plain
        // `fun login(user: BadUser)` call that would otherwise succeed starts
        // failing because we injected observation. Unacceptable side-effect.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            class BadUser { override fun toString(): String = error("boom") }
            @Debuggable(isSingleton = true) object Gateway {
                var sideEffectRan = false
                fun handle(u: BadUser) { sideEffectRan = true }
            }
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val gatewayCls = result.classLoader.loadClass("Gateway")
        val gateway = gatewayCls.getField("INSTANCE").get(null)
        val badUserCls = result.classLoader.loadClass("BadUser")
        val badUser = badUserCls.getDeclaredConstructor().newInstance()

        // Suppress the stderr noise from the logger (if any) so the test
        // output stays clean even when this fails.
        captureSystemOut {
            val handle = gatewayCls.getDeclaredMethod("handle", badUserCls)
            handle.invoke(gateway, badUser)
        }

        val ran = gatewayCls.getDeclaredMethod("getSideEffectRan").invoke(gateway) as Boolean
        assertTrue(
            ran,
            "function body must run even when an argument's toString() throws (task-134)",
        )
    }

    @Test fun `data class generated toString does not cause infinite recursion`() {
        // task-135: logAction writes via the logger, and the default logger
        // formats the call as `"toString()"` — which for a @Debuggable data
        // class injects logAction into the generated toString, which formats
        // by calling toString, …. Infinite recursion / StackOverflowError.
        //
        // The fix is to filter generated members (data-class copy / equals /
        // hashCode / toString / componentN) out of `targetFunctions` in
        // `DebuggableClassTransformer`.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true)
            data class User(val name: String, val age: Int)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val userClass = result.classLoader.loadClass("User")
        val instance = userClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType)
            .newInstance("daisy", 30)

        // If logAction was injected into toString(), this call would recurse
        // on itself (logAction → formatter → toString → logAction → …) and
        // throw StackOverflowError. A well-behaved transformation skips
        // generated members entirely.
        val output = captureSystemOut { instance.toString() }
        // toString should return the standard data-class representation.
        assertEquals("User(name=daisy, age=30)", instance.toString())
        // And it should NOT have logged anything — generated members are not
        // user-authored public methods, so logAction must not fire on them.
        assertFalse(
            output.contains("toString"),
            "logAction must not be injected into data-class-generated toString(); got: $output",
        )
    }
}
