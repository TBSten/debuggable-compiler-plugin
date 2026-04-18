package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gradle DSL の `debuggable { defaultLogger.set("com.example.X") }` 指定が
 * IR 変換時に per-class 未指定のクラスで採用されることを検証する。
 */
class GradleDefaultLoggerTests : CompilerTestBase() {

    @Test fun `Gradle defaultLogger receives method logs when no per-class logger`() {
        val result = compile(
            // language=kotlin
            """
            package com.example
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger

            object GlobalLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Global] " + message)
                }
            }

            @Debuggable(isSingleton = true)
            object Store {
                fun work() {}
            }
        """.trimIndent(), defaultLogger = "com.example.GlobalLogger")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Compilation failed:\n${result.messages}")

        val store = result.classLoader.loadClass("com.example.Store")
            .getDeclaredField("INSTANCE").get(null)
        val output = captureSystemOut { store.call("work") }
        assertTrue(output.contains("[Global]"), "Expected Gradle defaultLogger prefix, got: $output")
        assertFalse(output.contains("[Debuggable]"), "Default prefix should NOT appear, got: $output")
    }

    @Test fun `per-class logger beats Gradle defaultLogger`() {
        val result = compile(
            // language=kotlin
            """
            package com.example
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger

            object GlobalLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Global] " + message)
                }
            }

            object AuthLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Auth] " + message)
                }
            }

            @Debuggable(isSingleton = true, logger = AuthLogger::class)
            object AuthStore {
                fun signIn() {}
            }
        """.trimIndent(), defaultLogger = "com.example.GlobalLogger")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val store = result.classLoader.loadClass("com.example.AuthStore")
            .getDeclaredField("INSTANCE").get(null)
        val output = captureSystemOut { store.call("signIn") }
        assertTrue(output.contains("[Auth]"), "Per-class AuthLogger should win: $output")
        assertFalse(output.contains("[Global]"), "Gradle defaultLogger must not leak when per-class is set: $output")
    }

    @Test fun `Gradle defaultLogger routes Flow emissions`() {
        val result = compile(
            // language=kotlin
            """
            package com.example
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger
            import kotlinx.coroutines.flow.MutableStateFlow

            object GlobalLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Global] " + message)
                }
            }

            @Debuggable(isSingleton = true)
            object Store {
                val count = MutableStateFlow(0)
            }
        """.trimIndent(), defaultLogger = "com.example.GlobalLogger")
        val obj = result.classLoader.loadClass("com.example.Store")
            .getDeclaredField("INSTANCE").get(null)
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 42
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Global]"), "Gradle defaultLogger should route Flow logs: $output")
        assertTrue(output.contains("count"), "Property name should appear: $output")
    }

    @Test fun `empty defaultLogger falls back to DefaultDebugLogger (legacy behaviour)`() {
        // No defaultLogger specified → existing DefaultDebugLogger fallback still works.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Store { fun work() {} }
        """.trimIndent())
        val obj = result.getObject("Store")
        val output = captureSystemOut { obj.call("work") }
        assertTrue(output.contains("[Debuggable]"), "Fallback should use DefaultDebugLogger's Stdout: $output")
    }

    @Test fun `unresolved Gradle defaultLogger emits compiler error`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Store { fun work() {} }
        """.trimIndent(), defaultLogger = "com.example.DoesNotExist")
        // Compilation still succeeds because the IR safety-net is a soft ERROR report, but
        // the message collector should carry the diagnostic.
        assertTrue(
            result.messages.contains("could not be resolved"),
            "Expected unresolved-FQN error message, got:\n${result.messages}",
        )
    }
}
