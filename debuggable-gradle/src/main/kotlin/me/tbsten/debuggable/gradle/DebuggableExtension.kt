package me.tbsten.debuggable.gradle

import org.gradle.api.provider.Property

abstract class DebuggableExtension {
    /** Master switch. When false, the plugin performs no IR transformation at all. */
    abstract val enabled: Property<Boolean>

    /** When false, Flow/State property observation injection is skipped. */
    abstract val observeFlow: Property<Boolean>

    /** When false, method logAction injection is skipped. */
    abstract val logAction: Property<Boolean>

    /**
     * Fully-qualified class name of a `DebugLogger` singleton `object` to use as the
     * compile-time default logger. When set, the compiler plugin wires IR-injected
     * logging calls through this object directly (no runtime setup required).
     *
     * Leaving empty falls back to `DefaultDebugLogger` (whose `current` can still be
     * replaced at runtime). `@Debuggable(logger = X::class)` on a class takes precedence
     * over this setting.
     */
    abstract val defaultLogger: Property<String>

    init {
        enabled.convention(true)
        observeFlow.convention(true)
        logAction.convention(true)
        defaultLogger.convention("")
    }
}
