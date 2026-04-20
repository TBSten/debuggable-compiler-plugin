package me.tbsten.debuggable.runtime.stack

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

@InternalDebuggableApi
actual fun captureCallStack(maxDepth: Int): String {
    val frames = Thread.currentThread().stackTrace
        .dropWhile { frame ->
            frame.className.startsWith("me.tbsten.debuggable") ||
                frame.className == "java.lang.Thread"
        }
        .take(maxDepth)
    return frames.joinToString("\n") { "  at $it" }
}
