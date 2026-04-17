@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.visitors

import me.tbsten.debuggable.compiler.util.AnnotationFqNames
import me.tbsten.debuggable.compiler.util.isDebuggableTarget
import me.tbsten.debuggable.compiler.util.isFlow
import me.tbsten.debuggable.compiler.util.isState
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class DebuggableClassTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    private val symbolProvider = SymbolProvider(pluginContext)
    private val messageCollector = pluginContext.messageCollector

    override fun visitClass(declaration: IrClass) = super.visitClass(declaration).also {
        if (declaration.hasAnnotation(AnnotationFqNames.DEBUGGABLE)) {
            transformDebuggableClass(declaration)
        }
    }

    private fun transformDebuggableClass(irClass: IrClass) {
        val isSingleton = irClass.isSingletonDebuggable()

        val properties = irClass.declarations.filterIsInstance<IrProperty>()
        val functions = irClass.declarations.filterIsInstance<IrSimpleFunction>()

        // Validate: @Debuggable on non-singleton non-AutoCloseable class
        if (!isSingleton) {
            val isAutoCloseable = irClass.superTypes.any { type ->
                val fqn = type.classFqName?.asString()
                fqn == "java.lang.AutoCloseable" || fqn == "kotlin.AutoCloseable"
            }
            if (!isAutoCloseable) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@Debuggable requires isSingleton=true or the class to implement AutoCloseable",
                )
                return
            }
        }

        // Validate: conflicting @FocusDebuggable + @IgnoreDebuggable on same element
        val conflictProperty = properties.firstOrNull {
            it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) &&
                it.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        }
        val conflictFunction = functions.firstOrNull {
            it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) &&
                it.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        }
        if (conflictProperty != null || conflictFunction != null) {
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "@FocusDebuggable and @IgnoreDebuggable cannot be used together on the same element",
            )
            return
        }

        val focusMode = properties.any { it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) } ||
            functions.any { it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) }

        // Warn: @FocusDebuggable on non-Flow/State property
        if (focusMode) {
            properties.filter { it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) }.forEach { prop ->
                val type = prop.getter?.returnType
                if (type != null && !type.isDebuggableTarget()) {
                    messageCollector.report(
                        CompilerMessageSeverity.WARNING,
                        "@FocusDebuggable on '${prop.name}' has no effect: property is not a Flow or State type",
                    )
                }
            }
        }

        val targetProperties = properties.filter { property ->
            val type = property.getter?.returnType ?: return@filter false
            if (!type.isDebuggableTarget()) return@filter false
            if (focusMode) property.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
            else !property.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        }

        val targetFunctions = functions.filter { fn ->
            if (fn.isFakeOverride || fn.visibility != DescriptorVisibilities.PUBLIC) return@filter false
            if (fn.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)) return@filter false
            if (focusMode) fn.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
            else true
        }

        val registryField = if (!isSingleton) {
            addRegistryProperty(irClass, symbolProvider, pluginContext)
        } else {
            null
        }

        if (!isSingleton && registryField != null) {
            injectRegistryClose(irClass, registryField, symbolProvider, pluginContext)
        }

        injectLogAction(targetFunctions, symbolProvider, pluginContext)

        injectFlowObservations(
            irClass = irClass,
            targetProperties = targetProperties,
            isSingleton = isSingleton,
            registryField = registryField,
            symbolProvider = symbolProvider,
            pluginContext = pluginContext,
        )
    }

    private fun IrClass.isSingletonDebuggable(): Boolean {
        val annotation = getAnnotation(AnnotationFqNames.DEBUGGABLE) ?: return false
        val arg = annotation.arguments.firstOrNull() ?: return false
        if (arg !is IrConst) return false
        return try {
            arg.javaClass.getMethod("getValue").invoke(arg) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
