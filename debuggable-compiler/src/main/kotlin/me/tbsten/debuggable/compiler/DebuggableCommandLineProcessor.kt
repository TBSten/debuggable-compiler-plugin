package me.tbsten.debuggable.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal val KEY_ENABLED = CompilerConfigurationKey.create<Boolean>("debuggable.enabled")

@OptIn(ExperimentalCompilerApi::class)
class DebuggableCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = BuildConfig.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Enable or disable the Debuggable compiler plugin",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == "enabled") {
            configuration.put(KEY_ENABLED, value.toBooleanStrict())
        }
    }
}
