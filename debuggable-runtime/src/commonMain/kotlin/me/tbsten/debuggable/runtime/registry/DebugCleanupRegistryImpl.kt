package me.tbsten.debuggable.runtime.registry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class DebugCleanupRegistryCommonImpl : DebugCleanupRegistry {
    private val job = SupervisorJob()
    override val coroutineScope: CoroutineScope = CoroutineScope(job)

    // Immutable-list swap pattern: every register allocates a new list so
    // close()'s snapshot-and-iterate is never observing a mutating collection.
    private var cleanups: List<() -> Unit> = emptyList()
    private var closed = false

    override fun register(cleanup: () -> Unit) {
        if (closed) {
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
