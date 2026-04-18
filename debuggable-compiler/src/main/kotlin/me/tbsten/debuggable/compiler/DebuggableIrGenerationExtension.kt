package me.tbsten.debuggable.compiler

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.IrInjectorLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Delegates the actual IR transformation to whichever [IrInjector] implementation
 * `IrInjectorLoader` picks at runtime (based on the Kotlin compiler version on the
 * classpath). The impl classes live in sibling `debuggable-compiler-compat-kXX`
 * modules and are discovered via `ServiceLoader` of [IrInjector.Factory].
 *
 * Staying thin here means nothing in this class — or its transitive static
 * classloading — references version-sensitive IR APIs, so the main plugin JAR can
 * load cleanly on every supported Kotlin version even if the picked impl can't.
 */
class DebuggableIrGenerationExtension(
    private val options: DebuggableOptions = DebuggableOptions(
        observeFlow = true,
        logAction = true,
        defaultLoggerFqn = "",
    ),
) : IrGenerationExtension {

    private val injector: IrInjector by lazy { IrInjectorLoader.load() }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        injector.transform(
            moduleFragment,
            pluginContext,
            IrInjector.Options(
                observeFlow = options.observeFlow,
                logAction = options.logAction,
                defaultLoggerFqn = options.defaultLoggerFqn,
            ),
        )
    }
}
