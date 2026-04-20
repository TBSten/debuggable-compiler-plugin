package example

import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramLoggingTest {

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
    fun `diagram=true logs variable names at call site`() {
        val a = 3
        val b = 4
        DiagramCalc.add(a, b)
        val log = logger.messages.firstOrNull { "add" in it }
            ?: error("expected a diagram log for 'add', got: ${logger.messages}")
        assertTrue("a=" in log, "expected 'a=' capture in log, got: $log")
        assertTrue("b=" in log, "expected 'b=' capture in log, got: $log")
        assertTrue("3" in log, "expected value 3 in log, got: $log")
        assertTrue("4" in log, "expected value 4 in log, got: $log")
    }

    @Test
    fun `diagram=true does not emit logAction-style evaluated-args log`() {
        val a = 5
        val b = 6
        DiagramCalc.add(a, b)
        // logAction would produce "add(5, 6)"; diagram logging should NOT produce that
        val logActionStyle = logger.messages.any { it.trim() == "add(5, 6)" }
        assertFalse(logActionStyle, "diagram=true must not emit logAction-style log, got: ${logger.messages}")
    }

    @Test
    fun `diagram=true log contains inline comment with captures`() {
        val x = 10
        val y = 20
        DiagramCalc.add(x, y)
        val log = logger.messages.firstOrNull { "add" in it }
            ?: error("expected a diagram log for 'add', got: ${logger.messages}")
        assertTrue("//" in log, "expected inline comment ('//') in diagram log, got: $log")
    }
}
