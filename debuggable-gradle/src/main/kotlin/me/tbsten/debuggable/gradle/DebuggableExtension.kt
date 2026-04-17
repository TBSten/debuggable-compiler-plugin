package me.tbsten.debuggable.gradle

import org.gradle.api.provider.Property

abstract class DebuggableExtension {
    abstract val enabled: Property<Boolean>

    init {
        enabled.convention(true)
    }
}
