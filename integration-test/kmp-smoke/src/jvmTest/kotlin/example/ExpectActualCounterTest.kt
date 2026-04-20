package example

import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that `@Debuggable` on an expect+actual singleton object does NOT
 * double-inject logAction. A single tick() call must yield exactly one
 * "tick" log entry. Covers task-121.
 */
class ExpectActualCounterTest {

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
    fun `tick fires logAction exactly once per call (no expect-actual double)`() {
        ExpectActualCounter.tick()
        val tickMessages = logger.messages.filter { "tick" in it }
        assertEquals(
            1, tickMessages.size,
            "expected exactly one 'tick' log entry, got ${tickMessages.size}: $tickMessages",
        )
    }

    @Test
    fun `tick still runs user body and returns incremented counter`() {
        // Side-effect confirmation: the injected pre-logAction statement must
        // not disrupt the user body. First call returns 1, second returns 2.
        val first = ExpectActualCounter.tick()
        val second = ExpectActualCounter.tick()
        assertTrue(
            second > first,
            "actual body must still run after logAction injection",
        )
    }
}
