package me.tbsten.debuggable.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.debuggable.runtime.logging.DebugLogger

/**
 * A single captured log entry.
 *
 * @property sequence Monotonic counter assigned at capture time. Useful as
 *   a stable Compose list key when the same [message] is logged twice.
 * @property timestampMillis Epoch-millis at capture time.
 * @property message The raw message passed to [DebugLogger.log].
 */
data class LogEntry(
    val sequence: Long,
    val timestampMillis: Long,
    val message: String,
)

/**
 * A [DebugLogger] that keeps the most recent [bufferSize] messages in memory
 * and exposes them as a [StateFlow] so a Compose UI can re-render when new
 * entries arrive.
 *
 * Typical wiring at app startup:
 *
 * ```
 * val uiLogger = UiDebugLogger(bufferSize = 500)
 * DefaultDebugLogger.current = uiLogger
 * // then render `DebuggableLogViewer(uiLogger)` anywhere in your Compose tree.
 * ```
 *
 * Thread safety: [log] uses a monitor so concurrent emissions from multiple
 * coroutines do not lose entries or corrupt the ring buffer. The [entries]
 * snapshot is observable via normal Kotlin-Flow APIs.
 *
 * Ring behaviour: when more than [bufferSize] messages are captured, the
 * oldest are dropped — the viewer always shows the tail. Set [bufferSize]
 * higher if you need long-lived scrollback (e.g. a whole app session).
 */
class UiDebugLogger(
    val bufferSize: Int = 1000,
    private val clock: () -> Long = DefaultMonotonicClock,
) : DebugLogger {

    init {
        require(bufferSize >= 1) { "bufferSize must be >= 1, was $bufferSize" }
    }

    private val lock = Any()
    private var sequence: Long = 0L
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** The currently-visible log entries, oldest first. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    override fun log(message: String) {
        val entry = synchronized(lock) {
            LogEntry(
                sequence = ++sequence,
                timestampMillis = clock(),
                message = message,
            )
        }
        _entries.update { current ->
            val next = if (current.size >= bufferSize) {
                current.drop(current.size - bufferSize + 1) + entry
            } else {
                current + entry
            }
            next
        }
    }

    /** Removes all captured entries. */
    fun clear() {
        _entries.value = emptyList()
    }
}

// Local helper to keep the file platform-agnostic — `StateFlow.update` is from
// `kotlinx.coroutines.flow` but we import MutableStateFlow above already.
private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val prev = value
        val next = transform(prev)
        if (compareAndSet(prev, next)) return
    }
}

// `kotlin.time.Clock.System` is marked `@ExperimentalTime` as of the pinned
// Kotlin version, and we target `apiVersion = KOTLIN_2_0` so opting into
// experimental APIs from newer language versions is fragile. Use the
// non-experimental `TimeSource.Monotonic` and snapshot a baseline epoch on
// first call so timestamps remain millisecond-valued but monotonically
// increasing. For test determinism callers can pass their own `clock` lambda.
private val DefaultMonotonicClock: () -> Long = run {
    val start = kotlin.time.TimeSource.Monotonic.markNow()
    val epochAtStart = 0L
    // `markNow().elapsedNow()` doesn't require opt-in; that's what we want.
    val fn: () -> Long = {
        epochAtStart + start.elapsedNow().inWholeMilliseconds
    }
    fn
}
