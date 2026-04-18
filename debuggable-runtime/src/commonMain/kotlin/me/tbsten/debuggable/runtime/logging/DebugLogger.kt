package me.tbsten.debuggable.runtime.logging

/**
 * Replaceable sink for all debug logs emitted by the Debuggable runtime.
 *
 * Declare a singleton `object` implementing this interface to use it with
 * [me.tbsten.debuggable.runtime.annotations.Debuggable]'s `logger` parameter,
 * or assign it to [DefaultDebugLogger.current] at application startup to
 * redirect all `@Debuggable` logs globally.
 */
fun interface DebugLogger {
    fun log(message: String)

    companion object {
        /** Default implementation that prints to stdout with a `[Debuggable]` prefix. */
        val Stdout: DebugLogger = DebugLogger { println("[Debuggable] $it") }
    }
}
