package me.tbsten.debuggable.runtime.logging

import kotlin.concurrent.Volatile

/**
 * Application-wide default logger sink.
 *
 * Override by assigning [current] during application startup, e.g.:
 *
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         DefaultDebugLogger.current = DebugLogger { Log.d("Debuggable", it) }
 *     }
 *     }
 * ```
 *
 * `@Debuggable` classes that do not declare their own `logger` argument route
 * log output through this object, so runtime changes to [current] take effect
 * immediately for all such classes.
 *
 * Thread safety: [current] is marked `@Volatile`. Single-assignment during
 * startup is the intended usage; heavy concurrent reconfiguration is out of
 * scope for this minimal implementation.
 */
object DefaultDebugLogger : DebugLogger {
    // Start from the platform-appropriate default: Logcat on Android so
    // Android consumers don't need any setup to see logs, plain stdout
    // (`println` → `console.log` on JS/Wasm) everywhere else. Override by
    // assigning [current] at startup if you want a different sink.
    @Volatile
    private var _current: DebugLogger = platformDefaultLogger()

    var current: DebugLogger
        get() = _current
        set(value) {
            _current = value
        }

    override fun log(message: String) = _current.log(message)
}
