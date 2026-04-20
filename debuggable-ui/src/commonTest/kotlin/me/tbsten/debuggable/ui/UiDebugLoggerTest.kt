package me.tbsten.debuggable.ui

import me.tbsten.debuggable.runtime.logging.DebugLogger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiDebugLoggerTest {

    @Test
    fun `captures each message in order with monotonic sequence`() {
        val logger = UiDebugLogger(bufferSize = 10)
        logger.log(null, "a", DebugLogger.NoValue)
        logger.log(null, "count", 42)
        logger.log(null, "label", null)
        val captured = logger.entries.value
        assertContentEquals(listOf("a", "count: 42", "label: null"), captured.map { it.message })
        assertTrue(captured.map { it.sequence } == listOf(1L, 2L, 3L), "sequences should be 1..3, got ${captured.map { it.sequence }}")
    }

    @Test
    fun `ring buffer drops oldest when full`() {
        val logger = UiDebugLogger(bufferSize = 3)
        repeat(5) { logger.log(null, "msg$it", DebugLogger.NoValue) }
        val captured = logger.entries.value
        assertEquals(3, captured.size)
        assertContentEquals(listOf("msg2", "msg3", "msg4"), captured.map { it.message })
    }

    @Test
    fun `clear resets captured entries but keeps sequence monotonic`() {
        val logger = UiDebugLogger(bufferSize = 10)
        logger.log(null, "old", DebugLogger.NoValue)
        logger.clear()
        logger.log(null, "new", DebugLogger.NoValue)
        val captured = logger.entries.value
        assertEquals(1, captured.size)
        assertEquals("new", captured[0].message)
        // sequence continues so UI keys stay unique after clear
        assertEquals(2L, captured[0].sequence)
    }

    @Test
    fun `bufferSize below 1 is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { UiDebugLogger(bufferSize = 0) }
    }

    @Test
    fun `timestamp is taken from injected clock`() {
        var now = 100L
        val logger = UiDebugLogger(bufferSize = 10, clock = { now })
        logger.log(null, "a", DebugLogger.NoValue)
        now = 500L
        logger.log(null, "b", DebugLogger.NoValue)
        val captured = logger.entries.value
        assertEquals(100L, captured[0].timestampMillis)
        assertEquals(500L, captured[1].timestampMillis)
    }
}
