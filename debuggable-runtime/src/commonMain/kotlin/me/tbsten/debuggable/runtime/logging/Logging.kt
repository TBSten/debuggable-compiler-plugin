package me.tbsten.debuggable.runtime.logging

internal fun debugLog(message: String) = println("[Debuggable] $message")

fun logAction(name: String, vararg args: Any?) {
    debugLog("$name(${args.joinToString()})")
}
