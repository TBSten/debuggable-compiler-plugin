package me.tbsten.debuggable.compiler.compat.k2020

import me.tbsten.debuggable.compiler.compat.MessageCollectorHolder
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
 * property. As a best-effort fallback we emit diagnostics with `w:` / `e:` prefixes to both
 * stdout and stderr, so that kotlinc's CLI pipes surface them and kctfork-based tests (which
 * only capture the configured `messageOutputStream`, not stderr) still see the warnings.
 */
internal fun IrPluginContext.messageCollectorK20Compat(): MessageCollector {
    // Preferred path: the main plugin's registrar stashed the compiler's own MessageCollector
    // in a thread-local before the IR extension ran. Using that means our diagnostics flow
    // through the same sink as the rest of the compiler's output.
    MessageCollectorHolder.get()?.let { return it }

    // Fallback #1: the legacy `createDiagnosticReporter` (throws on K2 2.0.20 – 2.1.10).
    // Fallback #2: write `w:` / `e:` prefixed lines to stdout+stderr so at least kotlinc
    // CLI surfaces them. kctfork-based tests will be missing the MessageCollectorHolder
    // bridge only if the registrar hasn't run — not expected, but keep the fallback.
    return try {
        createDiagnosticReporter("me.tbsten.debuggable")
    } catch (_: Throwable) {
        PrefixedFallbackMessageCollector
    }
}

/**
 * When the compiler's own `MessageCollector` is unreachable (K2 on 2.0.20 – 2.1.10), we
 * still need diagnostics to surface somewhere. Write to stdout using the `w:` / `e:` line
 * prefixes kotlinc itself uses — that way both the Gradle build log *and* the kctfork
 * test harness (which only inherits stdout) pick them up.
 */
private object PrefixedFallbackMessageCollector : MessageCollector {
    override fun clear() {}
    override fun hasErrors(): Boolean = false
    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        val prefix = when (severity) {
            CompilerMessageSeverity.ERROR -> "e:"
            CompilerMessageSeverity.WARNING -> "w:"
            CompilerMessageSeverity.INFO -> "i:"
            else -> "v:"
        }
        val loc = location?.let { " ${it.path}:${it.line}:${it.column}:" }.orEmpty()
        // stdout so kctfork's messageOutputStream captures it; mirror to stderr so
        // real-world kotlinc builds still see it in the expected stream.
        println("$prefix$loc $message [Debuggable]")
        System.err.println("$prefix$loc $message [Debuggable]")
    }
}
