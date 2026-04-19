@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugCleanupRegistryTest {

    @Test
    fun `close executes all registered cleanups`() {
        val log = mutableListOf<String>()
        val registry = DebugCleanupRegistry()
        registry.register { log.add("first") }
        registry.register { log.add("second") }

        registry.close()

        assertEquals(listOf("first", "second"), log)
    }

    @Test
    fun `close clears registry so second close does nothing`() {
        var count = 0
        val registry = DebugCleanupRegistry()
        registry.register { count++ }

        registry.close()
        registry.close()

        assertEquals(1, count)
    }

    @Test
    fun `close on empty registry does not throw`() {
        val registry = DebugCleanupRegistry()
        registry.close()
    }

    @Test
    fun `register after close is ignored on next close`() {
        var called = false
        val registry = DebugCleanupRegistry()
        registry.close()
        registry.register { called = true }
        registry.close()
        assertTrue(called, "cleanup registered after first close should run on second close")
    }
}
