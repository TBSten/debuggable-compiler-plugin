package me.tbsten.debuggable.runtime.stack

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

@InternalDebuggableApi
actual fun captureCallStack(maxDepth: Int): String = ""
