package me.tbsten.debuggable.runtime.logging

/** Native default (iOS / macOS / Linux / mingw) — `println` via [DebugLogger.Stdout]. */
internal actual fun platformDefaultLogger(): DebugLogger = DebugLogger.Stdout
