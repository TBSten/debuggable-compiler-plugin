package me.tbsten.debuggable.compiler.compat.k2000

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.k2000.visitors.DebuggableClassTransformer
import me.tbsten.debuggable.compiler.compat.k2000.visitors.DiagramCallTransformer
import me.tbsten.debuggable.compiler.compat.k2000.visitors.LocalVariableTransformer
import me.tbsten.debuggable.compiler.compat.k2000.visitors.LoggerResolver
import me.tbsten.debuggable.compiler.compat.k2000.visitors.SymbolProvider
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * [IrInjector] implementation for Kotlin 2.0.0 – 2.1.10. Compiled against
 * `kotlin-compiler-embeddable:2.0.21`, so the emitted bytecode only references APIs
 * that existed before the 2.1.20 overhaul of `IrMemberAccessExpression` argument and
 * receiver handling and before `IrPluginContext.messageCollector` was introduced.
 *
 * Key differences from the k21 / k23 impls:
 * - `putValueArgument(index, expr)` instead of `arguments[param] = expr`
 * - `extensionReceiver = expr` / `dispatchReceiver = expr` instead of `insert…Receiver`
 * - `valueParameters` instead of `parameters.filter { kind == Regular }`
 * - `pluginContext.createDiagnosticReporter(pluginId)` instead of `pluginContext.messageCollector`
 * - `annotation.getValueArgument(index)` instead of `annotation.arguments[index]`
 */
class DebuggableIrInjectorK2000 : IrInjector {
    override fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        options: IrInjector.Options,
    ) {
        moduleFragment.transformChildrenVoid(DebuggableClassTransformer(pluginContext, options))
        if (options.observeFlow) {
            moduleFragment.transformChildrenVoid(LocalVariableTransformer(pluginContext, options))
        }
        if (options.logAction) {
            val symbolProvider = SymbolProvider(pluginContext)
            val loggerResolver = LoggerResolver(symbolProvider, options, pluginContext)
            moduleFragment.transformChildrenVoid(DiagramCallTransformer(pluginContext, symbolProvider, loggerResolver))
        }
        moduleFragment.patchDeclarationParents()
    }

    class Factory : IrInjector.Factory {
        override val minVersion: String = "2.0.0"
        override fun create(): IrInjector = DebuggableIrInjectorK2000()
    }
}
