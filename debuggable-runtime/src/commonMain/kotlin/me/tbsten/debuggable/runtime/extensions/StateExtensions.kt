package me.tbsten.debuggable.runtime.extensions

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tbsten.debuggable.runtime.logging.debugLog
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry

fun <T> State<T>.debuggableState(
    name: String,
    registry: DebugCleanupRegistry = DebugCleanupRegistry.Default,
): State<T> {
    snapshotFlow { value }
        .onEach { debugLog("$name: $it") }
        .launchIn(registry.coroutineScope)
    return this
}
