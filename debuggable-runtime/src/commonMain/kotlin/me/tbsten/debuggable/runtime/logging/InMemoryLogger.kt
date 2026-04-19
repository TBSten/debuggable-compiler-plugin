package me.tbsten.debuggable.runtime.logging

/**
 * A [DebugLogger] that records every message it receives into an in-memory list.
 *
 * Intended for tests — assert against the captured messages instead of
 * capturing stdout:
 *
 * ```
 * val logger = InMemoryLogger()
 * DefaultDebugLogger.current = logger
 * runTheCodeUnderTest()
 * assertTrue(logger.messages.any { "count: 42" in it })
 * ```
 *
 * Not safe for high-frequency multi-threaded logging — the internal list is
 * not synchronized across threads. Tests typically drive the code under test
 * from a single thread (or from cooperatively-scheduled coroutines on the
 * test dispatcher), which is the intended use case. If you need concurrent
 * appends, wrap it in your own [DebugLogger] with a [platformLock].
 *
 * Not intended for production logging volumes — memory grows unbounded until
 * [clear] is called.
 */
class InMemoryLogger : DebugLogger {
    private val _messages = mutableListOf<String>()

    override fun log(message: String) {
        _messages.add(message)
    }

    /** A stable snapshot of the captured messages at call time. */
    val messages: List<String>
        get() = snapshot()

    /** Returns a copy of the messages recorded so far. */
    fun snapshot(): List<String> = _messages.toList()

    /** Resets the recorded messages. */
    fun clear() {
        _messages.clear()
    }
}
