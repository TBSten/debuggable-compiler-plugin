package me.tbsten.debuggable.runtime.logging

/** JVM default — `println` via [DebugLogger.Stdout]. */
internal actual fun platformDefaultLogger(): DebugLogger = DebugLogger.Stdout
