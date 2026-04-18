package me.tbsten.debuggable.runtime.logging

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugLoggerTest {

    @BeforeTest
    @AfterTest
    fun resetDefault() {
        DefaultDebugLogger.current = DebugLogger.Stdout
    }

    @Test
    fun `SAM conversion creates a DebugLogger`() {
        val captured = mutableListOf<String>()
        val logger = DebugLogger { captured += it }
        logger.log("hello")
        logger.log("world")
        assertEquals(listOf("hello", "world"), captured)
    }

    @Test
    fun `DefaultDebugLogger delegates to current and can be replaced`() {
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { captured += it }

        DefaultDebugLogger.log("a")
        DefaultDebugLogger.log("b")

        assertEquals(listOf("a", "b"), captured)
    }

    @Test
    fun `restoring DefaultDebugLogger to Stdout resumes default behaviour`() {
        var customCalled = false
        DefaultDebugLogger.current = DebugLogger { customCalled = true }

        DefaultDebugLogger.current = DebugLogger.Stdout
        DefaultDebugLogger.log("back to default")

        assertTrue(!customCalled, "Custom logger should not receive messages after reset")
    }

    @Test
    fun `logAction routes through DefaultDebugLogger`() {
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { captured += it }

        logAction("greet", "alice", 42)

        assertEquals(listOf("greet(alice, 42)"), captured)
    }
}
