package me.tbsten.debuggable.runtime.registry

class DebugCleanupRegistry : AutoCloseable {
    private val cleanups = mutableListOf<() -> Unit>()

    fun register(cleanup: () -> Unit) {
        cleanups.add(cleanup)
    }

    override fun close() {
        cleanups.forEach { it() }
        cleanups.clear()
    }
}
