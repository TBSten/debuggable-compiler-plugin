package me.tbsten.debuggable.runtime.stack

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

/**
 * Returns a formatted string of the current call stack up to [maxDepth] frames,
 * with Debuggable-internal frames stripped. Returns an empty string on platforms
 * that do not support stack introspection (JS, Wasm, Native).
 *
 * Called by the Debuggable compiler plugin when `@Debuggable(captureStack = true)` is set.
 */
@InternalDebuggableApi
expect fun captureCallStack(maxDepth: Int = 8): String
