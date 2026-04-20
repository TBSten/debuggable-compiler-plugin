@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.logging

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun format(name: String, v: Any?) =
    if (v === DebugLogger.NoValue) name else "$name: $v"

class DebugLoggerTest {

    @BeforeTest
    @AfterTest
    fun resetDefault() {
        DefaultDebugLogger.current = DebugLogger.Stdout
    }

    @Test
    fun `SAM conversion creates a DebugLogger`() {
        val captured = mutableListOf<String>()
        val logger = DebugLogger { _, name, v -> captured += format(name, v) }
        logger.log(null, "hello", DebugLogger.NoValue)
        logger.log(null, "count", 42)
        assertEquals(listOf("hello", "count: 42"), captured)
    }

    @Test
    fun `DefaultDebugLogger delegates to current and can be replaced`() {
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { _, name, v -> captured += format(name, v) }

        DefaultDebugLogger.log(null, "a", DebugLogger.NoValue)
        DefaultDebugLogger.log(null, "count", 2)

        assertEquals(listOf("a", "count: 2"), captured)
    }

    @Test
    fun `restoring DefaultDebugLogger to Stdout resumes default behaviour`() {
        var customCalled = false
        DefaultDebugLogger.current = DebugLogger { _, _, _ -> customCalled = true }

        DefaultDebugLogger.current = DebugLogger.Stdout
        DefaultDebugLogger.log(null, "back to default", DebugLogger.NoValue)

        assertTrue(!customCalled, "Custom logger should not receive messages after reset")
    }

    @Test
    fun `logAction routes through DefaultDebugLogger`() {
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { _, name, v -> captured += format(name, v) }

        logAction("greet", "alice", 42)

        assertEquals(listOf("greet(alice, 42)"), captured)
    }

    @Test
    fun `null property value is distinct from NoValue`() {
        val captured = mutableListOf<String>()
        DefaultDebugLogger.current = DebugLogger { _, name, v -> captured += format(name, v) }

        // A property set to null should log "name: null", not just "name"
        DefaultDebugLogger.log(null, "label", null)

        assertEquals(listOf("label: null"), captured)
    }
}
