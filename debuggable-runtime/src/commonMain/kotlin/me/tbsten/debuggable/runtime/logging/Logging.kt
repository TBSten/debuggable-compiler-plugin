package me.tbsten.debuggable.runtime.logging

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

@InternalDebuggableApi
fun logAction(name: String, vararg args: Any?, logger: DebugLogger = DefaultDebugLogger, stackTrace: String = "") {
    // `args.joinToString()` would call each arg's `toString()` directly and
    // propagate any exception out of the caller — breaking the function body
    // that this log call was injected in front of. Format each arg defensively
    // so a badly-behaved `toString()` turns into a log placeholder instead of
    // hijacking the caller's control flow.
    val formatted = args.joinToString { arg ->
        try {
            arg?.toString() ?: "null"
        } catch (t: Throwable) {
            "<toString threw ${t::class.simpleName ?: "Throwable"}>"
        }
    }
    val label = if (stackTrace.isEmpty()) "$name($formatted)" else "$name($formatted)\n$stackTrace"
    logger.log(null, label, DebugLogger.NoValue)
}
