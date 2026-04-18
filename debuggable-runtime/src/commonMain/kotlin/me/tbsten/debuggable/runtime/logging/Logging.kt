package me.tbsten.debuggable.runtime.logging

internal fun debugLog(message: String) = DefaultDebugLogger.log(message)

fun logAction(name: String, vararg args: Any?, logger: DebugLogger = DefaultDebugLogger) {
    logger.log("$name(${args.joinToString()})")
}
