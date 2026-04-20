package me.tbsten.debuggable.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StackTraceInjectorTests : CompilerTestBase() {

    @Test fun `captureStack=true appends call-site stack to log`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true, captureStack = true) object Calc {
                fun add(a: Int, b: Int): Int = a + b
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Calc")
        val output = captureSystemOut { obj.call("add", 1, 2) }
        assertTrue("add(1, 2)" in output, "method log entry expected, got: $output")
        // On JVM the stack trace is captured; caller must be in the output.
        assertTrue("StackTraceInjectorTests" in output || "at " in output,
            "stack trace expected in output, got: $output")
    }

    @Test fun `captureStack=false (default) does NOT append stack`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Calc {
                fun add(a: Int, b: Int): Int = a + b
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Calc")
        val output = captureSystemOut { obj.call("add", 3, 4) }
        assertTrue("add(3, 4)" in output, "method log entry expected, got: $output")
        assertFalse("  at " in output, "no stack trace expected without captureStack, got: $output")
    }
}
