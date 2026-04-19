package me.tbsten.debuggable.runtime.logging

/** JS default — `println` (Kotlin/JS routes it to `console.log`). */
internal actual fun platformDefaultLogger(): DebugLogger = DebugLogger.Stdout
