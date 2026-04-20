@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun format(name: String, v: Any?) =
    if (v === DebugLogger.NoValue) name else "$name: $v"

class StandardLoggersTest {

    @Test
    fun `SilentLogger discards every message`() {
        SilentLogger.log(null, "anything", DebugLogger.NoValue)
        SilentLogger.log(null, "label", null)
        SilentLogger.log(null, "count", 42)
    }

    @Test
    fun `PrefixedLogger forwards with prefix to delegate`() {
        val captured = mutableListOf<String>()
        val delegate = DebugLogger { _, name, v -> captured += format(name, v) }
        val logger = PrefixedLogger("[MyApp]", delegate)

        logger.log(null, "hello", DebugLogger.NoValue)
        logger.log(null, "count", 42)

        assertEquals(listOf("[MyApp] hello", "[MyApp] count: 42"), captured)
    }

    @Test
    fun `PrefixedLogger defaults to Stdout delegate`() {
        val logger = PrefixedLogger("[X]")
        logger.log(null, "no crash expected", DebugLogger.NoValue)
    }

    @Test
    fun `PrefixedLogger can be chained`() {
        val captured = mutableListOf<String>()
        val sink = DebugLogger { _, name, v -> captured += format(name, v) }
        val inner = PrefixedLogger("[inner]", sink)
        val outer = PrefixedLogger("[outer]", inner)

        outer.log(null, "message", DebugLogger.NoValue)

        assertEquals(listOf("[inner] [outer] message"), captured)
    }

    @Test
    fun `SilentLogger can be installed as DefaultDebugLogger current`() {
        val original = DefaultDebugLogger.current
        DefaultDebugLogger.current = SilentLogger
        try {
            DefaultDebugLogger.log(null, "should be dropped", DebugLogger.NoValue)
            logAction("action", "arg")
            assertTrue(true)
        } finally {
            DefaultDebugLogger.current = original
        }
    }

    @Test
    fun `InMemoryLogger captures messages in order`() {
        val logger = InMemoryLogger()
        logger.log(null, "first", DebugLogger.NoValue)
        logger.log(null, "second", DebugLogger.NoValue)
        logger.log(null, "third", DebugLogger.NoValue)
        assertEquals(listOf("first", "second", "third"), logger.messages)
    }

    @Test
    fun `InMemoryLogger formats property and value`() {
        val logger = InMemoryLogger()
        logger.log(null, "count", 42)
        assertEquals(listOf("count: 42"), logger.messages)
    }

    @Test
    fun `InMemoryLogger formats null value as null string`() {
        val logger = InMemoryLogger()
        logger.log(null, "label", null)
        assertEquals(listOf("label: null"), logger.messages)
    }

    @Test
    fun `InMemoryLogger omits value for NoValue sentinel`() {
        val logger = InMemoryLogger()
        logger.log(null, "action()", DebugLogger.NoValue)
        assertEquals(listOf("action()"), logger.messages)
    }

    @Test
    fun `InMemoryLogger snapshot is independent of future writes`() {
        val logger = InMemoryLogger()
        logger.log(null, "a", DebugLogger.NoValue)
        val snap = logger.snapshot()
        logger.log(null, "b", DebugLogger.NoValue)
        assertEquals(listOf("a"), snap)
        assertEquals(listOf("a", "b"), logger.messages)
    }

    @Test
    fun `InMemoryLogger clear resets state`() {
        val logger = InMemoryLogger()
        logger.log(null, "x", DebugLogger.NoValue)
        logger.log(null, "y", DebugLogger.NoValue)
        logger.clear()
        assertTrue(logger.messages.isEmpty())
        logger.log(null, "z", DebugLogger.NoValue)
        assertEquals(listOf("z"), logger.messages)
    }

    @Test
    fun `CompositeLogger forwards to every delegate in order`() {
        val a = InMemoryLogger()
        val b = InMemoryLogger()
        val composite = CompositeLogger(a, b)

        composite.log(null, "hello", DebugLogger.NoValue)
        composite.log(null, "count", 42)

        assertEquals(listOf("hello", "count: 42"), a.messages)
        assertEquals(listOf("hello", "count: 42"), b.messages)
    }

    @Test
    fun `CompositeLogger with empty list is a no-op`() {
        val logger = CompositeLogger(emptyList())
        logger.log(null, "anything", DebugLogger.NoValue)
        assertTrue(true)
    }

    @Test
    fun `CompositeLogger visits remaining delegates when one throws`() {
        val captured = InMemoryLogger()
        val composite = CompositeLogger(
            DebugLogger { _, _, _ -> error("boom") },
            captured,
        )

        val thrown = runCatching { composite.log(null, "x", DebugLogger.NoValue) }.exceptionOrNull()
        assertTrue(thrown != null, "expected rethrown exception")
        assertEquals(listOf("x"), captured.messages)
    }
}
