package me.tbsten.debuggable.runtime.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry

/**
 * Starts a side-effect observation that logs each emission of this Flow via [logger].
 *
 * This function is **side-effect only**: it launches a coroutine in [registry]'s scope
 * and returns `this` unchanged. Collecting the returned Flow does NOT double-observe —
 * the log observation runs independently in the background.
 *
 * The observation runs until [registry] is closed (for AutoCloseable classes) or until
 * the process ends (for singletons using [DebugCleanupRegistry.Default]).
 *
 * Called automatically by the Debuggable compiler plugin; not intended for direct use.
 */
@InternalDebuggableApi
fun <T> Flow<T>.debuggableFlow(
    name: String,
    registry: DebugCleanupRegistry = DebugCleanupRegistry.Default,
    logger: DebugLogger = DefaultDebugLogger,
): Flow<T> {
    onEach { logger.log(null, name, it) }
        .launchIn(registry.coroutineScope)
    return this
}
