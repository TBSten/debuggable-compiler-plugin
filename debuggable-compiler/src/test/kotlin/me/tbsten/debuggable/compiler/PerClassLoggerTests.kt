package me.tbsten.debuggable.compiler

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `@Debuggable(logger = X::class)` で特定クラスのログだけ別 sink に流す。
 */
class PerClassLoggerTests : CompilerTestBase() {

    @Test fun `per-class logger receives method logs instead of DefaultDebugLogger`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger

            object AuthLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Auth] " + message)
                }
            }

            @Debuggable(isSingleton = true, logger = AuthLogger::class)
            object AuthStore {
                fun signIn(email: String) {}
            }
        """.trimIndent())
        val obj = result.getObject("AuthStore")
        val output = captureSystemOut { obj.call("signIn", "alice@example.com") }
        assertTrue(output.contains("[Auth]"), "Expected per-class logger prefix, got: $output")
        assertTrue(output.contains("signIn"), "Expected method name in log, got: $output")
        assertFalse(output.contains("[Debuggable]"), "Default prefix should NOT appear, got: $output")
    }

    @Test fun `per-class logger receives Flow logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger
            import kotlinx.coroutines.flow.MutableStateFlow

            object AuthLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Auth] " + message)
                }
            }

            @Debuggable(isSingleton = true, logger = AuthLogger::class)
            object AuthStore {
                val token = MutableStateFlow("")
            }
        """.trimIndent())
        val obj = result.getObject("AuthStore")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("token").apply { isAccessible = true }.get(obj) as MutableStateFlow<String>).value = "abc"
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Auth]"), "Expected per-class logger prefix for Flow, got: $output")
        assertTrue(output.contains("token"), "Expected property name in log, got: $output")
    }

    @Test fun `class without per-class logger still uses DefaultDebugLogger`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable

            @Debuggable(isSingleton = true)
            object DefaultStore {
                fun doWork() {}
            }
        """.trimIndent())
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { captured += it }
        try {
            val obj = result.getObject("DefaultStore")
            obj.call("doWork")
            assertTrue(captured.any { it.contains("doWork") },
                "Expected DefaultDebugLogger to capture method log, got: $captured")
        } finally {
            DefaultDebugLogger.current = DebugLogger.Stdout
        }
    }

    @Test fun `per-class logger and default logger can coexist`() {
        // Two objects: one with AuthLogger, one without.
        // The one without should go through DefaultDebugLogger; the one with should bypass it.
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger

            object AuthLogger : DebugLogger {
                override fun log(message: String) {
                    println("[Auth] " + message)
                }
            }

            @Debuggable(isSingleton = true, logger = AuthLogger::class)
            object AuthStore {
                fun auth() {}
            }

            @Debuggable(isSingleton = true)
            object PlainStore {
                fun plain() {}
            }
        """.trimIndent())
        val defaultCaptured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { defaultCaptured += it }
        try {
            val auth = result.getObject("AuthStore")
            val plain = result.getObject("PlainStore")

            val authOut = captureSystemOut { auth.call("auth") }
            assertTrue(authOut.contains("[Auth]"), "AuthStore should use AuthLogger: $authOut")

            plain.call("plain")
            assertTrue(defaultCaptured.any { it.contains("plain") },
                "PlainStore should use DefaultDebugLogger, got: $defaultCaptured")
            assertFalse(defaultCaptured.any { it.contains("auth(") },
                "AuthStore's logs should NOT leak into DefaultDebugLogger, got: $defaultCaptured")
        } finally {
            DefaultDebugLogger.current = DebugLogger.Stdout
        }
    }
}
