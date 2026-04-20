package me.tbsten.debuggable.runtime.registry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

@InternalDebuggableApi
actual fun DebugCleanupRegistry(): DebugCleanupRegistry = DebugCleanupRegistryJvmImpl()

// Extends java.io.Closeable so it can be passed to ViewModel.addCloseable(Closeable)
// in androidx.lifecycle:lifecycle-viewmodel 2.5+ (KMP editions use Closeable, not AutoCloseable).
internal class DebugCleanupRegistryJvmImpl : DebugCleanupRegistry, java.io.Closeable {
    private val job = SupervisorJob()
    override val coroutineScope: CoroutineScope = CoroutineScope(job)

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
