package me.tbsten.debuggable.compiler.compat.k2020

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Obtains a [MessageCollector] compatible with Kotlin 2.0.x running under K2.
 *
 * Why: Kotlin 2.0.x declares `IrPluginContext.createDiagnosticReporter(pluginId)` but the
 * K2-mode impl (`Fir2IrPluginContext`) throws `IllegalStateException("This API is not
 * supported for K2")`. It is also pre-2.1.20 so there is no `pluginContext.messageCollector`
 * property. As a best-effort fallback we emit diagnostics to stderr, which surfaces them
 * in the Gradle build output as "[Debuggable WARNING] …" lines.
 */
internal fun IrPluginContext.messageCollectorK20Compat(): MessageCollector {
    // First, try the legacy API and accept whatever it returns. If it throws (as it does
    // in K2 mode), fall back to a stderr-backed collector.
    return try {
        createDiagnosticReporter("me.tbsten.debuggable")
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
