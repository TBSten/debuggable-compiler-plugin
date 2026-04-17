package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class DebuggableIrGenerationExtensionTest {

    private fun compile(source: String): JvmCompilationResult {
        return KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Main.kt", source))
            compilerPluginRegistrars = listOf(DebuggableCompilerPluginRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
    }

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
