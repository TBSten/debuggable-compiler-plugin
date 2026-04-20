package me.tbsten.debuggable.runtime.extensions

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger

/**
 * Logs an assignment to a `var` property and returns [value] unchanged.
 *
 * Called by the Debuggable compiler plugin in generated setter bodies — it
 * wraps the `field = value` assignment so the new value is observed on every
 * mutation. The returned value replaces the original so the setter still
 * writes the caller-provided value to the backing field.
 *
 * ```
 * // Before (user code):
 * @Debuggable class Form { @FocusDebuggable var name: String = "" }
 *
 * // After IR transformation (conceptual):
 * class Form {
 *     var name: String = ""
 *         set(value) { field = debuggableSet("name", value, DefaultDebugLogger) }
 * }
 * ```
 *
 * Log format matches the observable-property path (`"name: value"`) so all
 * tracked mutations are consistent.
 *
 * Not intended for direct use — the plugin emits the calls for you.
 */
@InternalDebuggableApi
fun <T> debuggableSet(
    receiver: Any?,
    name: String,
    value: T,
    logger: DebugLogger = DefaultDebugLogger,
): T {
    logger.log(receiver, name, value)
    return value
}
