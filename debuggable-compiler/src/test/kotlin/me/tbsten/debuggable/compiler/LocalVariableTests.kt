package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `@Debuggable` on local `val` inside a function body.
 * The plugin wraps the function body in try-finally and tracks the Flow
 * within a per-function DebugCleanupRegistry that is released on function exit.
 */
class LocalVariableTests : CompilerTestBase() {

    @Test fun `local Flow with Debuggable compiles`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            fun runTask() {
                @Debuggable val local = MutableStateFlow(0)
                local.value = 1
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Expected success, got:\n${result.messages}")
    }

    @Test fun `local Flow changes are logged`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            class Runner {
                fun runTask() {
                    @Debuggable val local = MutableStateFlow(0)
                    local.value = 1
                    local.value = 2
                    Thread.sleep(100)
                }
            }
        """.trimIndent())
        val instance = result.getInstance("Runner")
        val output = captureSystemOut { instance.call("runTask") }
        assertTrue(output.contains("[Debuggable]"), "Expected [Debuggable] log, got: $output")
        assertTrue(output.contains("local"), "Expected variable name 'local' in log, got: $output")
    }

    @Test fun `local Flow observation stops after function exits`() {
        // After the function returns, the registry is closed and further mutations should NOT log.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            class Runner {
                private var escaped: MutableStateFlow<Int>? = null

                fun startAndEscape() {
                    @Debuggable val local = MutableStateFlow(0)
                    escaped = local
                    Thread.sleep(100) // let initial observation log
                }

                fun mutateEscaped() {
                    escaped?.value = 99
                }
            }
        """.trimIndent())
        val instance = result.getInstance("Runner")
        val startOutput = captureSystemOut {
            instance.call("startAndEscape")
            Thread.sleep(100)
        }
        assertTrue(startOutput.contains("[Debuggable]"), "Expected log during function, got: $startOutput")

        val afterOutput = captureSystemOut {
            instance.call("mutateEscaped")
            Thread.sleep(100)
        }
        // After the function exits, the observation is cancelled — no new logs.
        assertTrue(!afterOutput.contains("[Debuggable]"),
            "Expected no logs after function exit, got: $afterOutput")
    }

    @Test fun `non-Flow local with Debuggable is ignored (no transformation)`() {
        // @Debuggable on non-Flow locals should not crash — they're simply skipped.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            fun runTask() {
                @Debuggable val name: String = "hello"
                println(name)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Expected success, got:\n${result.messages}")
    }

    @Test fun `local Flow inside top-level function works`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            fun topLevelTask() {
                @Debuggable val counter = MutableStateFlow(0)
                counter.value = 42
                Thread.sleep(100)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val output = captureSystemOut {
            val mainKtClass = result.classLoader.loadClass("MainKt")
            val method = mainKtClass.getDeclaredMethod("topLevelTask")
            method.invoke(null)
        }
        assertTrue(output.contains("[Debuggable]"), "Expected log, got: $output")
        assertTrue(output.contains("counter"), "Expected variable name, got: $output")
    }

    @Test fun `multiple local Flows in same function`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            class Runner {
                fun runTask() {
                    @Debuggable val a = MutableStateFlow(0)
                    @Debuggable val b = MutableStateFlow("")
                    a.value = 1
                    b.value = "x"
                    Thread.sleep(100)
                }
            }
        """.trimIndent())
        val instance = result.getInstance("Runner")
        val output = captureSystemOut { instance.call("runTask") }
        assertTrue(output.contains("a"), "Expected variable 'a' in log, got: $output")
        assertTrue(output.contains("b"), "Expected variable 'b' in log, got: $output")
    }
}
