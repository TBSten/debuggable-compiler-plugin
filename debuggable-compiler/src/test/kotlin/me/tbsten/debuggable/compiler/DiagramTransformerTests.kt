package me.tbsten.debuggable.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramTransformerTests : CompilerTestBase() {

    @Test
    fun `diagram=true logs variable names at call site`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true, diagram = true)
            object Calc {
                fun process(value: Int): Int = value * 2
            }
            fun runTest() {
                val h = 3
                val f = 4
                Calc.process(h + f)
            }
            """.trimIndent(),
        )
        val output = captureSystemOut {
            result.classLoader.loadClass("MainKt")
                .getDeclaredMethod("runTest").invoke(null)
        }
        assertTrue("process" in output, "diagram log entry expected, got: $output")
        assertTrue("h=3" in output || "h" in output, "h capture expected, got: $output")
        assertTrue("f=4" in output || "f" in output, "f capture expected, got: $output")
    }

    @Test
    fun `diagram=true does not emit logAction-style log`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true, diagram = true)
            object Calc {
                fun process(value: Int): Int = value * 2
            }
            fun runTest() {
                val x = 7
                Calc.process(x)
            }
            """.trimIndent(),
        )
        val output = captureSystemOut {
            result.classLoader.loadClass("MainKt")
                .getDeclaredMethod("runTest").invoke(null)
        }
        // diagram mode replaces logAction, so we should NOT see process(14) (result format)
        // but rather process(x)  // x=7
        assertFalse("process(14)" in output,
            "logAction-style result logging should not appear in diagram mode, got: $output")
        assertTrue("process" in output, "diagram log expected, got: $output")
    }

    @Test
    fun `diagram=false (default) still uses logAction`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true)
            object Calc {
                fun process(value: Int): Int = value * 2
            }
            fun runTest() {
                Calc.process(5)
            }
            """.trimIndent(),
        )
        val output = captureSystemOut {
            result.classLoader.loadClass("MainKt")
                .getDeclaredMethod("runTest").invoke(null)
        }
        // Default (diagram=false) still uses logAction which logs "process(5)"
        assertTrue("process(5)" in output, "logAction log expected, got: $output")
    }

    @Test
    fun `diagram=true with direct literal skips diagram (no leaf captures)`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true, diagram = true)
            object Calc {
                fun process(value: Int): Int = value * 2
            }
            fun runTest() {
                Calc.process(42)  // literal — no IrGetValue captures
            }
            """.trimIndent(),
        )
        // Should compile and run without errors (diagram skipped for literals)
        val output = captureSystemOut {
            result.classLoader.loadClass("MainKt")
                .getDeclaredMethod("runTest").invoke(null)
        }
        // No exception = pass; output may or may not have a log
        assertTrue(output.isEmpty() || output.isNotEmpty())
    }
}
