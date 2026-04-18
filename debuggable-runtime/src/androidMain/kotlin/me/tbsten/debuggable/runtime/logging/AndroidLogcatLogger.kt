package me.tbsten.debuggable.runtime.logging

import android.util.Log

/**
 * A [DebugLogger] that routes messages to Android Logcat via `Log.d(tag, message)`.
 *
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         DefaultDebugLogger.current = AndroidLogcatLogger
 *         // or with a custom tag:
 *         DefaultDebugLogger.current = AndroidLogcatLogger("MyApp")
 *     }
 * }
 * ```
 *
 * The default [AndroidLogcatLogger] object uses the tag `"Debuggable"`.
 * Use the constructor to pass a custom tag.
 */
class AndroidLogcatLogger(private val tag: String) : DebugLogger {
    override fun log(message: String) {
        Log.d(tag, message)
    }

    companion object : DebugLogger {
        private val default = AndroidLogcatLogger("Debuggable")
        override fun log(message: String) = default.log(message)
    }
}
