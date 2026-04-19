package me.tbsten.debuggable.runtime.logging

/** Wasm/JS default — `println` (Kotlin/Wasm-JS routes it to `console.log`). */
internal actual fun platformDefaultLogger(): DebugLogger = DebugLogger.Stdout
