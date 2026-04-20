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
    /**
     * Called on every observable event.
     *
     * @param receiver The object on which the property lives, or `null` when
     *   the context is not available (e.g. singleton objects, function calls).
     * @param propertyName Property name, function call expression, or any other
     *   label that identifies what changed.
     * @param value The new value for property mutations. Pass [NoValue] (not
     *   `null`) to signal that there is no associated value — used for function
     *   call logs and diagram logs. `null` is a legitimate property value and
     *   must not be confused with the absence of a value.
     */
    fun log(receiver: Any?, propertyName: String, value: Any?)

    /**
     * Sentinel that means "this log event has no associated value".
     *
     * Callers like [logAction] and [logDiagram] pass [NoValue] instead of
     * `null` so that logger implementations can distinguish between:
     * - a property set to `null` → `log(null, "field", null)`
     * - a function call with no value → `log(null, "action()", NoValue)`
     */
    object NoValue

    /**
     * Default implementation that prints to stdout with a `[Debuggable]` prefix.
     *
     * Declared as a nested `object` (not a property) so it satisfies the
     * `@Debuggable(logger = …)` requirement of being a singleton object —
     * needed on Android / JVM consumers that want to opt into this sink
     * explicitly rather than relying on the platform default.
     */
    object Stdout : DebugLogger {
        override fun log(receiver: Any?, propertyName: String, value: Any?) {
            if (value === NoValue) println("[Debuggable] $propertyName")
            else println("[Debuggable] $propertyName: $value")
        }
    }
}
