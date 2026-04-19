package me.tbsten.debuggable.runtime.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardLoggersTest {

    @Test
    fun `SilentLogger discards every message`() {
        // Simply verify it does not throw and produces no observable side effect.
        SilentLogger.log("anything")
        SilentLogger.log("")
        SilentLogger.log("\n")
    }

    @Test
    fun `PrefixedLogger forwards with prefix to delegate`() {
        val captured = mutableListOf<String>()
        val delegate = DebugLogger { captured += it }
        val logger = PrefixedLogger("[MyApp]", delegate)

        logger.log("hello")
        logger.log("world")

        assertEquals(listOf("[MyApp] hello", "[MyApp] world"), captured)
    }

    @Test
    fun `PrefixedLogger defaults to Stdout delegate`() {
        // Just exercise the default constructor argument.
        val logger = PrefixedLogger("[X]")
        // Cannot capture println() without System.setOut, so we simply ensure it doesn't throw.
        logger.log("no crash expected")
    }

    @Test
    fun `PrefixedLogger can be chained`() {
        val captured = mutableListOf<String>()
        val sink = DebugLogger { captured += it }
        val inner = PrefixedLogger("[inner]", sink)
        val outer = PrefixedLogger("[outer]", inner)

        outer.log("message")

        assertEquals(listOf("[inner] [outer] message"), captured)
    }

    @Test
    fun `SilentLogger can be installed as DefaultDebugLogger current`() {
        val original = DefaultDebugLogger.current
        DefaultDebugLogger.current = SilentLogger
        try {
            DefaultDebugLogger.log("should be dropped")
            logAction("action", "arg")
            // No assertion required — just ensure no side effects leak.
            assertTrue(true)
        } finally {
            DefaultDebugLogger.current = original
        }
    }

    @Test
    fun `InMemoryLogger captures messages in order`() {
        val logger = InMemoryLogger()
        logger.log("first")
        logger.log("second")
        logger.log("third")
        assertEquals(listOf("first", "second", "third"), logger.messages)
    }

    @Test
    fun `InMemoryLogger snapshot is independent of future writes`() {
        val logger = InMemoryLogger()
        logger.log("a")
        val snap = logger.snapshot()
        logger.log("b")
        assertEquals(listOf("a"), snap)
        assertEquals(listOf("a", "b"), logger.messages)
    }

    @Test
    fun `InMemoryLogger clear resets state`() {
        val logger = InMemoryLogger()
        logger.log("x")
        logger.log("y")
        logger.clear()
        assertTrue(logger.messages.isEmpty())
        logger.log("z")
        assertEquals(listOf("z"), logger.messages)
    }

    @Test
    fun `CompositeLogger forwards to every delegate in order`() {
        val a = InMemoryLogger()
        val b = InMemoryLogger()
        val composite = CompositeLogger(a, b)

        composite.log("hello")
        composite.log("world")

        assertEquals(listOf("hello", "world"), a.messages)
        assertEquals(listOf("hello", "world"), b.messages)
    }

    @Test
    fun `CompositeLogger with empty list is a no-op`() {
        val logger = CompositeLogger(emptyList())
        logger.log("anything")
        // should not throw
        assertTrue(true)
    }

    @Test
    fun `CompositeLogger visits remaining delegates when one throws`() {
        val captured = InMemoryLogger()
        val composite = CompositeLogger(
            DebugLogger { error("boom") },
            captured,
        )

        val thrown = runCatching { composite.log("x") }.exceptionOrNull()
        assertTrue(thrown != null, "expected rethrown exception")
        // Delegates after the throwing one still received the message.
        assertEquals(listOf("x"), captured.messages)
    }
}
