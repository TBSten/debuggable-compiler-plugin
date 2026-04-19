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

    /**
     * Default implementation that prints to stdout with a `[Debuggable]` prefix.
     *
     * Declared as a nested `object` (not a property) so it satisfies the
     * `@Debuggable(logger = …)` requirement of being a singleton object —
     * needed on Android / JVM consumers that want to opt into this sink
     * explicitly rather than relying on the platform default.
     */
    object Stdout : DebugLogger {
        override fun log(message: String) {
            println("[Debuggable] $message")
        }
    }
}
