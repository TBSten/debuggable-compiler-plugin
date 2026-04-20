package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `@Debuggable(logger = X::class)` / Gradle `defaultLogger` の妥当性検証。
 * バリデーションは IR 変換フェーズで行われる (設計メモ参照)。
 */
class LoggerValidationTests : CompilerTestBase() {

    @Test fun `logger pointing to non-object class is compile error`() = err("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.logging.DebugLogger
        class NotAnObjectLogger : DebugLogger {
            override fun log(receiver: Any?, propertyName: String, value: Any?) {}
        }
        @Debuggable(isSingleton = true, logger = NotAnObjectLogger::class)
        object Store { fun work() {} }
    """)

    @Test fun `logger pointing to object NOT implementing DebugLogger is compile error`() = err("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.logging.DebugLogger
        // Type system would normally reject this, but just in case the check catches abuse via casts.
        // Simulate it by using an object that happens to be on the classpath but isn't a DebugLogger:
        // We'll just use a valid type-match but verify the happy path passes — non-DebugLogger is
        // rejected by Kotlin's type system first, so this test asserts the type-system rejection.
        object NotALogger
        // The following line intentionally does NOT compile because of Kotlin's own KClass<out DebugLogger> constraint.
        // If this test fails (= compiles OK), we'd need an explicit IR check.
        @Debuggable(isSingleton = true, logger = NotALogger::class)
        object Store { fun work() {} }
    """)

    @Test fun `valid object implementing DebugLogger compiles`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.logging.DebugLogger
            object ValidLogger : DebugLogger {
                override fun log(receiver: Any?, propertyName: String, value: Any?) {}
            }
            @Debuggable(isSingleton = true, logger = ValidLogger::class)
            object Store { fun work() {} }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Valid logger should compile:\n${result.messages}")
    }

    @Test fun `no logger specified compiles (uses default)`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Store { fun work() {} }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test fun `Gradle defaultLogger FQN not found emits diagnostic`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Store { fun work() {} }
        """.trimIndent(), defaultLogger = "com.example.DoesNotExist")
        // Compilation can still succeed (soft ERROR via message collector), but the diagnostic must be present.
        assertTrue(
            result.messages.contains("could not be resolved"),
            "Expected unresolved-FQN diagnostic, got:\n${result.messages}",
        )
    }

    private fun err(source: String) {
        val result = compile(source.trimIndent())
        assertEquals(
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "Expected compilation error but got:\n${result.messages}",
        )
    }
}
