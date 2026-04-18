package me.tbsten.debuggable.runtime.extensions

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry

/**
 * Starts a side-effect observation that logs each change of this Compose [State] via [debugLog].
 *
 * Converts state changes to a Flow via [snapshotFlow], then behaves identically to
 * [debuggableFlow]: the observation runs in the background in [registry]'s scope and
 * `this` is returned unchanged.
 *
 * Called automatically by the Debuggable compiler plugin; not intended for direct use.
 */
fun <T> State<T>.debuggableState(
    name: String,
    registry: DebugCleanupRegistry = DebugCleanupRegistry.Default,
    logger: DebugLogger = DefaultDebugLogger,
): State<T> {
    snapshotFlow { value }
        .onEach { logger.log("$name: $it") }
        .launchIn(registry.coroutineScope)
    return this
}
