package me.tbsten.debuggable.compiler.compat.k21

import me.tbsten.debuggable.compiler.compat.IrInjector
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Kotlin 2.1.20 – 2.1.21 implementation of the Debuggable IR transformation.
 *
 * TODO: port the full visitor suite from `debuggable-compiler-compat-k23` and adapt it
 * to the 2.1.x API surface (notably the old `IrBuilderWithScope.irCall / irString / …`
 * overloads that 2.2.0 demoted to `IrBuilder`). Until then this is a stub that throws
 * if it is ever picked by [me.tbsten.debuggable.compiler.compat.IrInjectorLoader].
 */
class DebuggableIrInjectorK21 : IrInjector {
    override fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        options: IrInjector.Options,
    ) {
        throw NotImplementedError(
            "debuggable-compiler-compat-k21 has not been implemented yet. " +
                "Only Kotlin 2.2.0 and later are currently supported.",
        )
    }

    class Factory : IrInjector.Factory {
        override val minVersion: String = "2.1.20"
        override fun create(): IrInjector = DebuggableIrInjectorK21()
    }
}
