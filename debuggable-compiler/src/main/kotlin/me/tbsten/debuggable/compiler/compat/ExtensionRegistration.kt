package me.tbsten.debuggable.compiler.compat

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Registers an extension whose [companion] may be either a `ProjectExtensionDescriptor`
 * (Kotlin 2.3 and earlier) or an `ExtensionPointDescriptor` (Kotlin 2.4.0-Beta1+).
 *
 * Why: Kotlin 2.4.0-Beta1 changed the parent class of several compiler extension companions
 * from `ProjectExtensionDescriptor` to `ExtensionPointDescriptor`. A direct call to
 * `registerExtension(...)` compiled against 2.3.x emits bytecode that references the 2.3
 * signature (`registerExtension(ProjectExtensionDescriptor, Object)`), which no longer exists
 * in 2.4+. Reflecting over the `ExtensionStorage` instance's methods lets us find whichever
 * signature the current runtime exposes and dispatch to it.
 */
@OptIn(ExperimentalCompilerApi::class)
internal fun CompilerPluginRegistrar.ExtensionStorage.registerExtensionCompat(
    companion: Any,
    extension: Any,
) {
    val storageClass = this.javaClass
    val method = storageClass.methods.firstOrNull { m ->
        m.name == "registerExtension" &&
            m.parameterCount == 2 &&
            m.parameterTypes[0].isInstance(companion)
    } ?: error(
        "No compatible registerExtension(descriptor, extension) method found on " +
            "${storageClass.name}. Available: " +
            storageClass.methods.filter { it.name == "registerExtension" }
                .joinToString { it.toGenericString() }
    )
    method.invoke(this, companion, extension)
}
