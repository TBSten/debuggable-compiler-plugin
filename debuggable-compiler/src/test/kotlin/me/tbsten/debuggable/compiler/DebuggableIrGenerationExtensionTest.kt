package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sanity tests that the plugin is installable and doesn't crash on source that has nothing
 * to do with `@Debuggable`. Shares `CompilerTestBase.compile` so classpath filtering and
 * kctfork workarounds (see [CompilerTestBase]) apply here too.
 */
class DebuggableIrGenerationExtensionTest : CompilerTestBase() {

    @Test
    fun `plugin does not break compilation of plain code`() {
        val result = compile(
            """
            fun main() {
                println("hello")
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `plugin does not break compilation when Debuggable annotation is absent`() {
        val result = compile(
            """
            class SomeClass {
                val value: Int = 0
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
