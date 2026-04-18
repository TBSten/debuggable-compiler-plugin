package me.tbsten.debuggable.compiler.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Entry point for the Debuggable plugin's FIR extensions.
 *
 * Registered from [me.tbsten.debuggable.compiler.DebuggableCompilerPluginRegistrar]
 * via `FirExtensionRegistrarAdapter.registerExtension`.
 */
class DebuggableFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::DebuggableFirAdditionalCheckersExtension
    }
}
