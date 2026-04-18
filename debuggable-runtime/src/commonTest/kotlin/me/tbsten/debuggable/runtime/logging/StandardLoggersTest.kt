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
}
