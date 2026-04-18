package me.tbsten.debuggable.compiler

import me.tbsten.debuggable.compiler.visitors.DebuggableClassTransformer
import me.tbsten.debuggable.compiler.visitors.LocalVariableTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class DebuggableIrGenerationExtension(
    private val options: DebuggableOptions = DebuggableOptions(observeFlow = true, logAction = true),
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(DebuggableClassTransformer(pluginContext, options))
        if (options.observeFlow) {
            moduleFragment.transformChildrenVoid(LocalVariableTransformer(pluginContext))
        }
        moduleFragment.patchDeclarationParents()
    }
}
