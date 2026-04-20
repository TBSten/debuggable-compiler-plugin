@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.extensions

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class StateExtensionsTest {

    @Test
    fun `debuggableState returns the same state instance`() {
        val state = mutableStateOf(0)
        val registry = DebugCleanupRegistry()
        val result = state.debuggableState(null, "test", registry)
        assertSame(state, result)
        registry.close()
    }

    @Test
    fun `debuggableState stops observation when registry is closed`() = runTest {
        val state = mutableStateOf(0)
        val registry = DebugCleanupRegistry()

        state.debuggableState(null, "test", registry)

        val scope = registry.coroutineScope
        registry.close()
        assertFalse(scope.coroutineContext[Job]!!.isActive)
    }
}
