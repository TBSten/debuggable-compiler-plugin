package me.tbsten.debuggable.runtime.logging

/**
 * A [DebugLogger] that forwards each message to every [loggers] entry in order.
 *
 * Useful when you want to send logs to more than one sink — e.g. keep the
 * platform default (`Stdout` / `AndroidLogcatLogger`) while simultaneously
 * capturing to an [InMemoryLogger] for test assertions:
 *
 * ```
 * val captured = InMemoryLogger()
 * DefaultDebugLogger.current = CompositeLogger(DebugLogger.Stdout, captured)
 * ```
 *
 * Messages are forwarded synchronously in declaration order. A thrown
 * exception in one delegate will NOT prevent subsequent delegates from
 * receiving the message; exceptions are caught and rethrown as a single
 * aggregate after every delegate has been visited. This matches the
 * "best-effort fan-out" used by most tracing SDKs.
 */
class CompositeLogger(private val loggers: List<DebugLogger>) : DebugLogger {

    constructor(vararg loggers: DebugLogger) : this(loggers.toList())

    override fun log(receiver: Any?, propertyName: String, value: Any?) {
        var firstThrown: Throwable? = null
        for (logger in loggers) {
            try {
                logger.log(receiver, propertyName, value)
            } catch (t: Throwable) {
                if (firstThrown == null) firstThrown = t else firstThrown.addSuppressed(t)
            }
        }
        if (firstThrown != null) throw firstThrown
    }
}
