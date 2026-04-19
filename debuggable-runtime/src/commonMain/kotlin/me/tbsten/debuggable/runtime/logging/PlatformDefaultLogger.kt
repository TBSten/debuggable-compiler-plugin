package me.tbsten.debuggable.runtime.logging

/**
 * Platform-appropriate default [DebugLogger]. Used by [DefaultDebugLogger] as
 * its initial sink when the app hasn't installed its own.
 *
 * - Android → [AndroidLogcatLogger] (`Log.d("Debuggable", ...)`)
 * - Everywhere else → [DebugLogger.Stdout] (prints `[Debuggable] …` to stdout)
 *
 * Apps can always override with `DefaultDebugLogger.current = …` at startup,
 * or pass `@Debuggable(logger = …)` per-class; this just picks a useful
 * default so logs show up in the conventional place for each platform without
 * any setup.
 */
internal expect fun platformDefaultLogger(): DebugLogger
