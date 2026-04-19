package example

import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * commonTest — runs on every target whose tests aren't disabled in
 * `build.gradle.kts`. Uses [InMemoryLogger] to capture what the IR-injected
 * log sites emit so the assertion doesn't depend on stdout capture (which is
 * fragile across platforms).
 */
class SamplesTest {

    private lateinit var logger: InMemoryLogger
    private lateinit var original: me.tbsten.debuggable.runtime.logging.DebugLogger

    @BeforeTest
    fun setup() {
        logger = InMemoryLogger()
        original = DefaultDebugLogger.current
        DefaultDebugLogger.current = logger
    }

    @AfterTest
    fun teardown() {
        DefaultDebugLogger.current = original
    }

    @Test
    fun `logAction fires on Counter tick`() {
        Counter.tick()
        assertTrue(
            logger.messages.any { "tick" in it },
            "expected tick call to log, got: ${logger.messages}",
        )
    }

    @Test
    fun `setter override fires on UserForm name assignment`() {
        UserForm.name = "daisy"
        assertTrue(
            logger.messages.any { it == "name: daisy" },
            "expected 'name: daisy' in messages, got: ${logger.messages}",
        )
    }

    @Test
    fun `setter override fires on Int and Boolean too`() {
        UserForm.age = 30
        UserForm.agreedToTerms = true
        assertTrue(
            logger.messages.any { it == "age: 30" },
            "expected 'age: 30' in messages, got: ${logger.messages}",
        )
        assertTrue(
            logger.messages.any { it == "agreedToTerms: true" },
            "expected 'agreedToTerms: true' in messages, got: ${logger.messages}",
        )
    }
}
