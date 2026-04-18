package me.tbsten.debuggable.compiler

import me.tbsten.debuggable.compiler.BuildConfig
import me.tbsten.debuggable.compiler.compat.MessageCollectorHolder
import me.tbsten.debuggable.compiler.compat.registerExtensionCompat
import me.tbsten.debuggable.compiler.fir.DebuggableFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
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

        // Stash the compiler's MessageCollector so per-version IR injectors can report
        // diagnostics through the same sink kctfork / kotlinc capture. 2.0.20 – 2.1.10 have
        // no accessible MessageCollector from `IrPluginContext`, so without this bridge
        // their warnings/errors land on stderr and never reach `result.messages` in tests.
        //
        // Intentionally using `CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY` (deprecated in
        // current Kotlin — delegates to `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY`):
        // the field only moved to `CommonConfigurationKeys` in 2.0.20, so reading from the
        // new location link-fails on 2.0.0 / 2.0.10. The deprecated alias still exists on
        // every 2.x version.
        @Suppress("DEPRECATION_ERROR")
        MessageCollectorHolder.set(configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY])

        // Reflection-based registration to support both 2.3.x (ProjectExtensionDescriptor)
        // and 2.4.0-Beta1+ (ExtensionPointDescriptor). See compat/ExtensionRegistration.kt.
        registerExtensionCompat(
            FirExtensionRegistrarAdapter.Companion,
            DebuggableFirExtensionRegistrar(),
        )
        registerExtensionCompat(
            IrGenerationExtension.Companion,
            DebuggableIrGenerationExtension(options),
        )
    }
}

data class DebuggableOptions(
    val observeFlow: Boolean,
    val logAction: Boolean,
    val defaultLoggerFqn: String = "",
)
