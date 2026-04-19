package me.tbsten.debuggable.runtime.logging

/** Android default — logs flow to Logcat with tag `"Debuggable"`. */
internal actual fun platformDefaultLogger(): DebugLogger = AndroidLogcatLogger
