package me.tbsten.debuggable.compiler.compat.k21

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.k21.visitors.DebuggableClassTransformer
import me.tbsten.debuggable.compiler.compat.k21.visitors.LocalVariableTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * [IrInjector] implementation for Kotlin 2.1.20 – 2.1.21. Compiled against
 * `kotlin-compiler-embeddable:2.1.21`, so IR-builder helpers like `irCall` / `irString`
 * emit bytecode that references the `IrBuilderWithScope` receiver type used by
 * those Kotlin releases. Kotlin 2.2.0 demoted those helpers onto `IrBuilder`, so
 * that range is covered by the `k23` impl instead.
 *
 * Apart from the receiver types baked into the bytecode, the source is identical to
 * `debuggable-compiler-compat-k23` — the new `arguments[param] = expr`,
 * `insertExtensionReceiver` / `insertDispatchReceiver`, and `pluginContext.messageCollector`
 * APIs were all introduced in 2.1.20.
 */
class DebuggableIrInjectorK21 : IrInjector {
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
        override val minVersion: String = "2.1.20"
        override fun create(): IrInjector = DebuggableIrInjectorK21()
    }
}
