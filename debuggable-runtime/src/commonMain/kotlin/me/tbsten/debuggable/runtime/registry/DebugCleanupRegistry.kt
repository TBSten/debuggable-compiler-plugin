package me.tbsten.debuggable.runtime.registry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

interface DebugCleanupRegistry : AutoCloseable {
    val coroutineScope: CoroutineScope
    fun register(cleanup: () -> Unit)

    /** No-op implementation used for isSingleton=true classes. */
    object Default : DebugCleanupRegistry {
        private var _scope = CoroutineScope(SupervisorJob())
        override val coroutineScope: CoroutineScope get() = _scope
        override fun register(cleanup: () -> Unit) = Unit
        override fun close() = Unit

        // Cancels all running observation coroutines and creates a fresh scope.
        // Accessed via reflection in tests to prevent coroutine leaks between test cases.
        @Suppress("unused")
        private fun resetScope() {
            _scope.cancel()
            _scope = CoroutineScope(SupervisorJob())
        }
    }
}

@InternalDebuggableApi
expect fun DebugCleanupRegistry(): DebugCleanupRegistry
