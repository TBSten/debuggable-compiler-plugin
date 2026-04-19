package me.tbsten.debuggable.compiler.compat.k23

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.k23.visitors.DebuggableClassTransformer
import me.tbsten.debuggable.compiler.compat.k23.visitors.LocalVariableTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * [IrInjector] implementation for Kotlin 2.2.0 – 2.4.0-Beta1 (and any compatible later
 * version). Compiled against `kotlin-compiler-embeddable:2.3.20`; the APIs used here —
 * `arguments[param] = expr`, `insertExtensionReceiver/DispatchReceiver`,
 * `parameters.filter { kind == IrParameterKind.Regular }`, `IrBuilder.irCall/…` etc. —
 * exist in that entire range.
 */
class DebuggableIrInjector : IrInjector {
    override fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        options: IrInjector.Options,
    ) {
        moduleFragment.transformChildrenVoid(DebuggableClassTransformer(pluginContext, options))
        if (options.observeFlow) {
            moduleFragment.transformChildrenVoid(LocalVariableTransformer(pluginContext, options))
        }
        moduleFragment.patchDeclarationParents()
    }

    class Factory : IrInjector.Factory {
        override val minVersion: String = "2.2.0"
        override fun create(): IrInjector = DebuggableIrInjector()
    }
}
