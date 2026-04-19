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
fun DebugCleanupRegistry(): DebugCleanupRegistry = DebugCleanupRegistryImpl()

private class DebugCleanupRegistryImpl : DebugCleanupRegistry {
    private val job = SupervisorJob()
    override val coroutineScope: CoroutineScope = CoroutineScope(job)

    // Immutable-list swap pattern: every register allocates a new list so
    // close()'s snapshot-and-iterate is never observing a mutating collection.
    // Full thread-safety would need atomicfu — this at least removes the
    // ConcurrentModificationException class of bug and makes double-close a
    // no-op for the common (main-thread) usage.
    private var cleanups: List<() -> Unit> = emptyList()
    private var closed = false

    override fun register(cleanup: () -> Unit) {
        if (closed) {
            // Post-close register: run immediately so callers can still rely on
            // their cleanup firing rather than silently leaking.
            cleanup()
            return
        }
        cleanups = cleanups + cleanup
    }

    override fun close() {
        if (closed) return
        closed = true
        coroutineScope.cancel()
        val snapshot = cleanups
        cleanups = emptyList()
        snapshot.forEach { it() }
    }
}
