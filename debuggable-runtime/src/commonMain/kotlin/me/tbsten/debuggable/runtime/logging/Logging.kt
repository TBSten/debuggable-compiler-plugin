package me.tbsten.debuggable.runtime.logging

fun logAction(name: String, vararg args: Any?, logger: DebugLogger = DefaultDebugLogger) {
    logger.log("$name(${args.joinToString()})")
}
