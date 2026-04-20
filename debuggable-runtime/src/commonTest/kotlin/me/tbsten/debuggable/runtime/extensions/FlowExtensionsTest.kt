@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.extensions

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class FlowExtensionsTest {

    @Test
    fun `debuggableFlow observes emitted values in background`() = runTest {
        val flow = MutableStateFlow(0)
        val registry = DebugCleanupRegistry()

        flow.debuggableFlow(null, "test", registry)
        flow.value = 1
        flow.value = 2
        delay(100)

        registry.close()
    }

    @Test
    fun `debuggableFlow stops observation when registry is closed`() = runTest {
        val registry = DebugCleanupRegistry()
        val flow = MutableStateFlow(0)

        flow.debuggableFlow(null, "test", registry)

        val scope = registry.coroutineScope
        assertFalse(scope.isActive.not())

        registry.close()
        assertFalse(scope.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun `debuggableFlow returns the same flow instance`() {
        val flow = MutableStateFlow(0)
        val registry = DebugCleanupRegistry()
        val result = flow.debuggableFlow(null, "test", registry)
        assertSame(flow, result)
        registry.close()
    }
}
