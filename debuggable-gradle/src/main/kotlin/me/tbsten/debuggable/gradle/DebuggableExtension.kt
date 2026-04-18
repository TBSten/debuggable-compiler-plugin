package me.tbsten.debuggable.gradle

import org.gradle.api.provider.Property

abstract class DebuggableExtension {
    /** Master switch. When false, the plugin performs no IR transformation at all. */
    abstract val enabled: Property<Boolean>

    /** When false, Flow/State property observation injection is skipped. */
    abstract val observeFlow: Property<Boolean>

    /** When false, method logAction injection is skipped. */
    abstract val logAction: Property<Boolean>

    init {
        enabled.convention(true)
        observeFlow.convention(true)
        logAction.convention(true)
    }
}
