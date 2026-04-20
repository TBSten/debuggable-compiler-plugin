package me.tbsten.debuggable.runtime.logging

import java.io.File
import java.io.IOException

/**
 * A [DebugLogger] that appends every message to [file], one line per message.
 *
 * Intended for long-running desktop / Android sessions where you want a
 * persistent log you can inspect outside the app (e.g. `adb shell run-as … cat`
 * on Android, or a plain editor on the JVM).
 *
 * ```
 * val logger = FileLogger(File(context.cacheDir, "debuggable.log"))
 * DefaultDebugLogger.current = logger
 * ```
 *
 * Only available on the JVM and Android targets — other KMP targets do not
 * have `java.io.File`. Use [CompositeLogger] if you need to combine it with
 * an in-memory capture or another sink.
 *
 * Line format: `<message>\n`. No prefix / timestamp is added; wrap with
 * [PrefixedLogger] if you want one. Each call opens, writes, and closes the
 * file, so a crash mid-run still leaves a usable log — acceptable for typical
 * debug-log volumes but not a high-throughput sink.
 *
 * Thread safety: writes are serialized through an internal monitor so
 * concurrent callers of [log] will not interleave partial lines.
 *
 * @property file Target file. Parent directories are created on first write.
 * @property append When `true` (default) keeps the existing content and
 *   appends to it; when `false` the file is truncated on construction.
 */
class FileLogger(
    private val file: File,
    append: Boolean = true,
) : DebugLogger {

    private val lock = Any()

    init {
        file.parentFile?.mkdirs()
        if (!append) file.writeText("")
    }

    override fun log(receiver: Any?, propertyName: String, value: Any?) {
        val message = if (value === DebugLogger.NoValue) propertyName else "$propertyName: $value"
        synchronized(lock) {
            try {
                file.appendText("$message\n", Charsets.UTF_8)
            } catch (e: IOException) {
                // Surface via stderr — swallowing would hide disk-full /
                // permission issues that usually indicate real problems.
                System.err.println("[FileLogger] failed to write to $file: ${e.message}")
            }
        }
    }
}
