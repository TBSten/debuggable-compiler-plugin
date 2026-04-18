package me.tbsten.debuggable.compiler

import me.tbsten.debuggable.compiler.BuildConfig
import me.tbsten.debuggable.compiler.fir.DebuggableFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class DebuggableCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = BuildConfig.PLUGIN_ID

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration[KEY_ENABLED] ?: true
        if (!enabled) return
        val options = DebuggableOptions(
            observeFlow = configuration[KEY_OBSERVE_FLOW] ?: true,
            logAction = configuration[KEY_LOG_ACTION] ?: true,
            defaultLoggerFqn = configuration[KEY_DEFAULT_LOGGER].orEmpty(),
        )
        FirExtensionRegistrarAdapter.registerExtension(DebuggableFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(DebuggableIrGenerationExtension(options))
    }
}

data class DebuggableOptions(
    val observeFlow: Boolean,
    val logAction: Boolean,
    val defaultLoggerFqn: String = "",
)
