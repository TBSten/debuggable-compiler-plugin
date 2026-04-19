package me.tbsten.debuggable.compiler.compat

import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Thread-local side-channel for handing the compiler's [MessageCollector] from the main
 * plugin's registrar down to the per-version IR injectors.
 *
 * Why a side-channel instead of adding a parameter to [IrInjector.transform]:
 * - `IrInjector` is the stable API between the main plugin and every published compat
 *   module. Adding a parameter breaks binary compatibility with already-built impls.
 * - The compiler itself ties `MessageCollector` access to compile-specific APIs that
 *   differ across versions (`pluginContext.messageCollector` only from 2.1.20; a
 *   `createDiagnosticReporter` that throws under K2 before that).
 *
 * The main plugin reads the collector from the `CompilerConfiguration` at extension-
 * registration time and stashes it here; each injector reads it from the same thread
 * during `generate`. The thread-local scoping matches how the Kotlin compiler hands off
 * work to plugin extensions (single-threaded per compilation).
 */
object MessageCollectorHolder {
    private val current = ThreadLocal<MessageCollector>()

    fun set(collector: MessageCollector?) {
        if (collector == null) current.remove() else current.set(collector)
    }

    fun get(): MessageCollector? = current.get()

    fun clear() {
        current.remove()
    }
}
