package me.tbsten.debuggable.runtime.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tbsten.debuggable.runtime.logging.debugLog
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry

fun <T> Flow<T>.debuggableFlow(
    name: String,
    registry: DebugCleanupRegistry = DebugCleanupRegistry.Default,
): Flow<T> {
    onEach { debugLog("$name: $it") }
        .launchIn(registry.coroutineScope)
    return this
}
