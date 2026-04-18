@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2000.visitors

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.k2000.getAnnotationCompat
import me.tbsten.debuggable.compiler.compat.k2000.messageCollectorK20Compat
import me.tbsten.debuggable.compiler.compat.k2000.util.AnnotationFqNames
import me.tbsten.debuggable.compiler.compat.k2000.util.isDebuggableTarget
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class DebuggableClassTransformer(
    private val pluginContext: IrPluginContext,
    private val options: IrInjector.Options = IrInjector.Options(observeFlow = true, logAction = true),
) : IrElementTransformerVoid() {

    private val symbolProvider = SymbolProvider(pluginContext)
    private val loggerResolver = LoggerResolver(symbolProvider, options, pluginContext)

    // 2.0.x predates `pluginContext.messageCollector` and its `createDiagnosticReporter`
    // throws on K2 — see [messageCollectorK20Compat] for the fallback.
    private val messageCollector = pluginContext.messageCollectorK20Compat()

    override fun visitClass(declaration: IrClass) = super.visitClass(declaration).also {
        if (declaration.hasAnnotation(AnnotationFqNames.DEBUGGABLE)) {
            transformDebuggableClass(declaration)
        } else {
            warnStrayFocusIgnoreAnnotations(declaration)
        }
    }

    private fun warnStrayFocusIgnoreAnnotations(irClass: IrClass) {
        val properties = irClass.declarations.filterIsInstance<IrProperty>()
        val functions = irClass.declarations.filterIsInstance<IrSimpleFunction>()
        val hasStray = properties.any { p ->
            p.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) ||
                p.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        } || functions.any { f ->
            f.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) ||
                f.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        }
        if (hasStray) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "Class '${irClass.name}' has @FocusDebuggable/@IgnoreDebuggable members " +
                    "but is not annotated with @Debuggable — these annotations have no effect. " +
                    "Did you forget @Debuggable on the class?",
            )
        }
    }

    private fun transformDebuggableClass(irClass: IrClass) {
        val isSingleton = irClass.isSingletonDebuggable()

        val properties = irClass.declarations.filterIsInstance<IrProperty>()
        val functions = irClass.declarations.filterIsInstance<IrSimpleFunction>()

        if (isSingleton && irClass.kind != ClassKind.OBJECT) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "@Debuggable(isSingleton=true) on class '${irClass.name}': " +
                    "isSingleton=true is intended for object declarations. " +
                    "For classes with lifecycle, implement AutoCloseable instead.",
            )
        }

        if (!isSingleton) {
            if (!irClass.implementsAutoCloseable()) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@Debuggable requires isSingleton=true or the class to implement AutoCloseable",
                )
                return
            }
        }

        val loggerClass = irClass.extractDebuggableLoggerAnnotationValue()
        if (loggerClass != null) {
            val loggerOwner = loggerClass.owner
            if (loggerOwner.kind != ClassKind.OBJECT) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@Debuggable(logger = ${loggerOwner.name}::class): logger must be declared as an `object` singleton.",
                )
                return
            }
            val debugLoggerSymbol = symbolProvider.debugLoggerClass
            val implementsDebugLogger = loggerOwner.superTypes.any { it.classOrNull == debugLoggerSymbol }
            if (!implementsDebugLogger) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@Debuggable(logger = ${loggerOwner.name}::class): logger must implement DebugLogger.",
                )
                return
            }
        }

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

        if (targetProperties.isEmpty() && targetFunctions.isEmpty()) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "@Debuggable on '${irClass.name}' has no effect: " +
                    "no Flow/State properties or public methods to track",
            )
        }

        val registryField = if (!isSingleton) {
            addRegistryProperty(irClass, symbolProvider, pluginContext)
        } else {
            null
        }

        if (!isSingleton && registryField != null) {
            injectRegistryClose(irClass, registryField, symbolProvider, pluginContext)
        }

        if (options.logAction) {
            injectLogAction(targetFunctions, irClass, symbolProvider, loggerResolver, pluginContext)
        }

        if (options.observeFlow) {
            injectFlowObservations(
                irClass = irClass,
                targetProperties = targetProperties,
                isSingleton = isSingleton,
                registryField = registryField,
                symbolProvider = symbolProvider,
                loggerResolver = loggerResolver,
                pluginContext = pluginContext,
            )
        }
    }

    /**
     * Reads `@Debuggable(logger = X::class)` and returns X's IrClassSymbol,
     * or null when the sentinel `Nothing::class` is used (= default).
     */
    private fun IrClass.extractDebuggableLoggerAnnotationValue(): IrClassSymbol? {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return null
        // 2.0.x: use the legacy getValueArgument API; the newer `arguments[i]` was added in 2.1.20.
        val loggerArg = annotation.getValueArgument(1) as? IrClassReference ?: return null
        val symbol = loggerArg.symbol as? IrClassSymbol ?: return null
        if (symbol.owner.defaultType.classFqName?.asString() == "kotlin.Nothing") return null
        return symbol
    }

    private fun IrClass.implementsAutoCloseable(): Boolean =
        superTypes.any { type ->
            val fqn = type.classFqName?.asString()
            if (fqn == "java.lang.AutoCloseable" || fqn == "kotlin.AutoCloseable") return@any true
            type.classOrNull?.owner?.implementsAutoCloseable() ?: false
        }

    private fun IrClass.isSingletonDebuggable(): Boolean {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return false
        val arg = annotation.getValueArgument(0) ?: return false
        if (arg !is IrConst<*>) return false
        return (arg.value as? Boolean) ?: false
    }
}
