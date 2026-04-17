package me.tbsten.debuggable.runtime.registry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

interface DebugCleanupRegistry : AutoCloseable {
    val coroutineScope: CoroutineScope
    fun register(cleanup: () -> Unit)

    /** No-op implementation used for isSingleton=true classes. */
    object Default : DebugCleanupRegistry {
        override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
        override fun register(cleanup: () -> Unit) = Unit
        override fun close() = Unit
    }
}

fun DebugCleanupRegistry(): DebugCleanupRegistry = DebugCleanupRegistryImpl()

private class DebugCleanupRegistryImpl : DebugCleanupRegistry {
    private val job = SupervisorJob()
    override val coroutineScope: CoroutineScope = CoroutineScope(job)
    private val cleanups = mutableListOf<() -> Unit>()

    override fun register(cleanup: () -> Unit) {
        cleanups.add(cleanup)
    }

    override fun close() {
        coroutineScope.cancel()
        cleanups.forEach { it() }
        cleanups.clear()
    }
}
