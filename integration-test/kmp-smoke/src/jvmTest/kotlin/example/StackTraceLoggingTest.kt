package example

import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StackTraceLoggingTest {

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
        logger.clear()
    }

    @Test
    fun `captureStack=true appends caller frame to log`() {
        TracedCalc.compute(21)
        val log = logger.messages.first { "compute" in it }
        assertTrue(
            "StackTraceLoggingTest" in log,
            "expected caller class in stack trace, got: $log",
        )
    }

    @Test
    fun `captureStack=true does not leak internal debuggable frames`() {
        TracedCalc.compute(7)
        val log = logger.messages.first { "compute" in it }
        assertFalse(
            "me.tbsten.debuggable" in log,
            "internal frames must be stripped from stack trace, got: $log",
        )
    }

    @Test
    fun `captureStack=false (default) produces no stack trace in log`() {
        Counter.tick()
        val log = logger.messages.firstOrNull { "tick" in it } ?: return
        assertFalse(
            "\n  at " in log,
            "no stack trace expected when captureStack=false, got: $log",
        )
    }
}
