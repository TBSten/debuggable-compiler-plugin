package me.tbsten.debuggable.compiler

import me.tbsten.debuggable.compiler.visitors.DebuggableClassTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class DebuggableIrGenerationExtension(
    private val configuration: CompilerConfiguration,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(DebuggableClassTransformer(pluginContext))
        // TODO: Phase 3 - patchDeclarationParents() after actual IR modifications
    }
}
