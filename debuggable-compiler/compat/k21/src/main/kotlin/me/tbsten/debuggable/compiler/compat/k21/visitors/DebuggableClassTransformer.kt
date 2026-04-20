@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k21.visitors

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.AnnotationFqNames
import me.tbsten.debuggable.compiler.compat.k21.util.isDebuggableTarget
import me.tbsten.debuggable.compiler.compat.k21.util.isFlow
import me.tbsten.debuggable.compiler.compat.k21.util.isState
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.defaultType
import me.tbsten.debuggable.compiler.compat.k21.getAnnotationCompat
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class DebuggableClassTransformer(
    private val pluginContext: IrPluginContext,
    private val options: IrInjector.Options = IrInjector.Options(observeFlow = true, logAction = true),
) : IrElementTransformerVoid() {

    private val symbolProvider = SymbolProvider(pluginContext)
    private val loggerResolver = LoggerResolver(symbolProvider, options, pluginContext)
    private val messageCollector = pluginContext.messageCollector

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

        // Warn: isSingleton=true on a class (should be on an object declaration)
        if (isSingleton && irClass.kind != ClassKind.OBJECT) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "@Debuggable(isSingleton=true) on class '${irClass.name}': " +
                    "isSingleton=true is intended for object declarations. " +
                    "For classes with lifecycle, implement AutoCloseable instead.",
            )
        }

        val isViewModel = !isSingleton && irClass.extendsAndroidxViewModel()
        if (!isSingleton) {
            if (!irClass.implementsAutoCloseable() && !isViewModel) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "@Debuggable requires isSingleton=true, the class to implement AutoCloseable, " +
                        "or the class to extend androidx.lifecycle.ViewModel",
                )
                return
            }
        }

        // Validate: @Debuggable(logger = X::class) — X must be an object implementing DebugLogger.
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

        if (focusMode) {
            properties.filter { it.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE) }.forEach { prop ->
                val type = prop.getter?.returnType
                if (type != null && !type.isDebuggableTarget() && !prop.isVar) {
                    messageCollector.report(
                        CompilerMessageSeverity.WARNING,
                        "@FocusDebuggable on '${prop.name}' has no effect: property is neither a Flow/State " +
                            "nor a `var` with a backing field.",
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

        val setterOverrideProperties = properties.filter { property ->
            if (!property.isVar) return@filter false
            if (property.backingField == null) return@filter false
            if (property.setter?.isFakeOverride != false) return@filter false
            val type = property.getter?.returnType ?: return@filter false
            if (type.isDebuggableTarget()) return@filter false
            if (focusMode) property.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
            else !property.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)
        }

        val targetFunctions = functions.filter { fn ->
            if (fn.isFakeOverride || fn.visibility != DescriptorVisibilities.PUBLIC) return@filter false
            if (fn.hasAnnotation(AnnotationFqNames.IGNORE_DEBUGGABLE)) return@filter false
            // See k23 equivalent: skip generated / Any-override members.
            if (isGeneratedOrAnyOverride(fn)) return@filter false
            if (focusMode) fn.hasAnnotation(AnnotationFqNames.FOCUS_DEBUGGABLE)
            else true
        }

        // Warn: @Debuggable class with no trackable members
        if (targetProperties.isEmpty() && setterOverrideProperties.isEmpty() && targetFunctions.isEmpty()) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "@Debuggable on '${irClass.name}' has no effect: " +
                    "no Flow/State properties, var properties, or public methods to track",
            )
        }

        val registryField = if (!isSingleton) {
            addRegistryProperty(irClass, symbolProvider, pluginContext)
        } else {
            null
        }

        if (!isSingleton && registryField != null) {
            if (isViewModel) {
                injectRegistryViaAddCloseable(irClass, registryField, symbolProvider, pluginContext)
            } else {
                injectRegistryClose(irClass, registryField, symbolProvider, pluginContext)
            }
        }

        if (options.logAction && !irClass.diagramDebuggable()) {
            injectLogAction(
                functions = targetFunctions,
                owningClass = irClass,
                symbolProvider = symbolProvider,
                loggerResolver = loggerResolver,
                pluginContext = pluginContext,
                captureStack = irClass.captureStackDebuggable(),
            )
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

            if (setterOverrideProperties.isNotEmpty()) {
                injectSetterOverrides(
                    irClass = irClass,
                    targetVarProperties = setterOverrideProperties,
                    loggerResolver = loggerResolver,
                    symbolProvider = symbolProvider,
                    pluginContext = pluginContext,
                )
            }
        }
    }

    /**
     * Reads `@Debuggable(logger = X::class)` and returns X's IrClassSymbol,
     * or null when the sentinel `Nothing::class` is used (= default).
     */
    private fun IrClass.extractDebuggableLoggerAnnotationValue(): IrClassSymbol? {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return null
        val loggerArg = annotation.arguments.getOrNull(1) as? IrClassReference ?: return null
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

    private fun IrClass.extendsAndroidxViewModel(): Boolean =
        superTypes.any { type ->
            val fqn = type.classFqName?.asString()
            if (fqn == "androidx.lifecycle.ViewModel") return@any true
            type.classOrNull?.owner?.extendsAndroidxViewModel() ?: false
        }

    // Canonical member names that the Kotlin compiler either inherits from
    // Any or generates for data classes. Excluded from logAction injection.
    private val ANY_OR_DATA_GENERATED_NAMES = setOf("toString", "equals", "hashCode", "copy")

    private fun isGeneratedOrAnyOverride(fn: IrSimpleFunction): Boolean {
        val n = fn.name.asString()
        if (n in ANY_OR_DATA_GENERATED_NAMES) return true
        if (n.startsWith("component") && n.length > "component".length &&
            n.drop("component".length).all { it.isDigit() }
        ) {
            return true
        }
        return false
    }

    private fun IrClass.isSingletonDebuggable(): Boolean {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return false
        val arg = annotation.arguments.firstOrNull() ?: return false
        if (arg !is IrConst) return false
        // IrConst.value is internal in the Kotlin compiler module boundary, but the JVM
        // bytecode exposes getValue() publicly. Reflect to stay compatible across K2 versions.
        return try {
            arg.javaClass.getMethod("getValue").invoke(arg) as? Boolean ?: false
        } catch (_: NoSuchMethodException) {
            // Kotlin IR internal API changed — assume non-singleton to avoid misclassification.
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "Debuggable plugin: could not read isSingleton value from @Debuggable annotation " +
                    "on '${name}' (IrConst.getValue() not found). Treating as non-singleton.",
            )
            false
        }
    }

    private fun IrClass.diagramDebuggable(): Boolean {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return false
        val arg = annotation.arguments.getOrNull(3) ?: return false
        if (arg !is IrConst) return false
        return try {
            arg.javaClass.getMethod("getValue").invoke(arg) as? Boolean ?: false
        } catch (_: NoSuchMethodException) { false }
    }

    // @Debuggable(isSingleton, logger, captureStack) — captureStack is at index 2.
    private fun IrClass.captureStackDebuggable(): Boolean {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return false
        val arg = annotation.arguments.getOrNull(2) ?: return false
        if (arg !is IrConst) return false
        return try {
            arg.javaClass.getMethod("getValue").invoke(arg) as? Boolean ?: false
        } catch (_: NoSuchMethodException) {
            false
        }
    }
}
