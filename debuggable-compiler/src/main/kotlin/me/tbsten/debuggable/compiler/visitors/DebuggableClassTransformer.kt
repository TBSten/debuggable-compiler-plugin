@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.visitors

import me.tbsten.debuggable.compiler.util.AnnotationFqNames
import me.tbsten.debuggable.compiler.util.isDebuggableTarget
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class DebuggableClassTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    override fun visitClass(declaration: IrClass) = super.visitClass(declaration).also {
        if (declaration.hasAnnotation(AnnotationFqNames.DEBUGGABLE)) {
            transformDebuggableClass(declaration)
        }
    }

    private fun transformDebuggableClass(irClass: IrClass) {
        val focusMode = irClass.properties.any {
            it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
        }

        val targetProperties = irClass.properties.filter { property ->
            val type = property.getter?.returnType ?: return@filter false
            if (!type.isDebuggableTarget()) return@filter false
            if (focusMode) {
                property.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
            } else {
                !property.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
            }
        }.toList()

        val targetFunctions = irClass.functions.filter { function ->
            !function.isFakeOverride &&
                function.visibility == DescriptorVisibilities.PUBLIC
        }.toList()

        // TODO: Phase 3 - ViewModel/AutoCloseable に応じた $$debuggable_registry の注入
        // TODO: Phase 3 - targetProperties を debuggableState/debuggableFlow でラップ
        // TODO: Phase 3 - targetFunctions の先頭に logAction を注入
    }
}
