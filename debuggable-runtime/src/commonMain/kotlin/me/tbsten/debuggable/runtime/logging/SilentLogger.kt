package me.tbsten.debuggable.runtime.logging

/**
 * A [DebugLogger] that discards every message.
 *
 * Useful when you want to keep the plugin enabled (e.g. for staging builds where
 * other instrumentation still runs) but suppress the log output entirely.
 *
 * ```
 * DefaultDebugLogger.current = SilentLogger
 * ```
 */
object SilentLogger : DebugLogger {
    override fun log(message: String) {
        // Intentionally empty.
    }
}
