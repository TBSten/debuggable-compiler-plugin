package me.tbsten.debuggable.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal val KEY_ENABLED = CompilerConfigurationKey.create<Boolean>("debuggable.enabled")
internal val KEY_OBSERVE_FLOW = CompilerConfigurationKey.create<Boolean>("debuggable.observeFlow")
internal val KEY_LOG_ACTION = CompilerConfigurationKey.create<Boolean>("debuggable.logAction")
internal val KEY_DEFAULT_LOGGER = CompilerConfigurationKey.create<String>("debuggable.defaultLogger")

@OptIn(ExperimentalCompilerApi::class)
class DebuggableCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = BuildConfig.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Enable or disable the Debuggable compiler plugin entirely",
            required = false,
        ),
        CliOption(
            optionName = "observeFlow",
            valueDescription = "<true|false>",
            description = "Inject Flow/State property observation (default: true)",
            required = false,
        ),
        CliOption(
            optionName = "logAction",
            valueDescription = "<true|false>",
            description = "Inject method call logAction (default: true)",
            required = false,
        ),
        CliOption(
            optionName = "defaultLogger",
            valueDescription = "<fqn>",
            description = "FQN of a DebugLogger object to use when @Debuggable has no explicit logger; empty = DefaultDebugLogger",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(KEY_ENABLED, value.toBooleanStrict())
            "observeFlow" -> configuration.put(KEY_OBSERVE_FLOW, value.toBooleanStrict())
            "logAction" -> configuration.put(KEY_LOG_ACTION, value.toBooleanStrict())
            "defaultLogger" -> configuration.put(KEY_DEFAULT_LOGGER, value)
        }
    }
}
