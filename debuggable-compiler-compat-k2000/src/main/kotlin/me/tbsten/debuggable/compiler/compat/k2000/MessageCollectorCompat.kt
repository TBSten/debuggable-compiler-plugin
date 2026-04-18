package me.tbsten.debuggable.compiler.compat.k2000

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Obtains a [MessageCollector] compatible with Kotlin 2.0.0 – 2.0.10 running under K2.
 *
 * Three moving parts across this version window:
 * 1. `pluginContext.messageCollector` only exists from 2.1.20 onward.
 * 2. `createDiagnosticReporter(pluginId)` is present but in 2.0.0 – 2.0.10 it returns
 *    `IrMessageLogger`, which was renamed to `MessageCollector` in 2.0.20. Calling it
 *    from source compiled against 2.0.10 causes a type error; dispatch through reflection
 *    so the call site isn't bound to a specific return type at compile time.
 * 3. In K2 mode that legacy method throws `IllegalStateException` — K2 never wired it up.
 *    Catch everything and fall back to a stderr sink.
 */
internal fun IrPluginContext.messageCollectorK20Compat(): MessageCollector {
    return try {
        val method = this.javaClass.methods.firstOrNull {
            it.name == "createDiagnosticReporter" && it.parameterCount == 1
        }
        val result = method?.invoke(this, "me.tbsten.debuggable")
        if (result is MessageCollector) result else StderrMessageCollector
    } catch (_: Throwable) {
        StderrMessageCollector
    }
}

private object StderrMessageCollector : MessageCollector {
    override fun clear() {}
    override fun hasErrors(): Boolean = false
    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        val prefix = when (severity) {
            CompilerMessageSeverity.ERROR -> "[Debuggable ERROR]"
            CompilerMessageSeverity.WARNING -> "[Debuggable WARNING]"
            CompilerMessageSeverity.INFO -> "[Debuggable INFO]"
            else -> "[Debuggable]"
        }
        val loc = location?.let { " at ${it.path}:${it.line}:${it.column}" }.orEmpty()
        System.err.println("$prefix$loc $message")
    }
}
