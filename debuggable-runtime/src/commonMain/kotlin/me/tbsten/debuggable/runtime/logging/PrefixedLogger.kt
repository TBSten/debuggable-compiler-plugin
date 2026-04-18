package me.tbsten.debuggable.runtime.logging

/**
 * A [DebugLogger] that adds a prefix to every message and forwards to [delegate].
 *
 * ```
 * DefaultDebugLogger.current = PrefixedLogger("[MyApp]", DebugLogger.Stdout)
 * // → "[MyApp] count: 42"
 * ```
 *
 * Chain multiple prefixes or use with tag-style loggers (Timber, slf4j) by
 * providing a suitable [delegate].
 */
class PrefixedLogger(
    private val prefix: String,
    private val delegate: DebugLogger = DebugLogger.Stdout,
) : DebugLogger {
    override fun log(message: String) {
        delegate.log("$prefix $message")
    }
}
