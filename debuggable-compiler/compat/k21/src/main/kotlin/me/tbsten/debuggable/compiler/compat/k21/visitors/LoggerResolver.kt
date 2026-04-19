@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k21.visitors

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.k21.util.AnnotationFqNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.defaultType
import me.tbsten.debuggable.compiler.compat.k21.getAnnotationCompat

/**
 * Resolves the DebugLogger to pass as the `logger` argument of `debuggableFlow` /
 * `debuggableState` / `logAction` when the plugin injects those calls.
 *
 * Resolution order (from design doc `.local/change-debug-log.md` #5):
 * 1. `@Debuggable(logger = X::class)` on the owning class (Phase B-3)
 * 2. Gradle DSL `defaultLogger = "FQN"` (Phase B-5)
 * 3. Fallback: `DefaultDebugLogger` (delegates to its mutable `current`)
 */
internal class LoggerResolver(
    private val symbolProvider: SymbolProvider,
    private val options: IrInjector.Options,
    private val pluginContext: IrPluginContext? = null,
) {
    fun resolve(owningClass: IrClass?): IrExpression {
        // 1. @Debuggable(logger = X::class) per-class override
        val perClassLogger = owningClass?.extractDebuggableLoggerClass()
        if (perClassLogger != null) return objectValue(perClassLogger)

        // 2. Gradle DSL `defaultLogger = "FQN"` (module-wide compile-time default)
        if (options.defaultLoggerFqn.isNotEmpty()) {
            val resolved = symbolProvider.resolveLoggerByFqn(options.defaultLoggerFqn)
            if (resolved != null) return objectValue(resolved)

            // Safety net: the FIR checker should have caught this already.
            pluginContext?.messageCollector?.report(
                CompilerMessageSeverity.ERROR,
                "Debuggable: `defaultLogger` FQN '${options.defaultLoggerFqn}' could not be " +
                    "resolved on the classpath. Falling back to DefaultDebugLogger.",
            )
        }

        // 3. Fallback: DefaultDebugLogger
        return objectValue(symbolProvider.defaultDebugLoggerClass)
    }

    /**
     * Reads the `logger` argument of `@Debuggable` on [this].
     * Returns null when the annotation is absent, the `logger` is the sentinel
     * `Nothing::class`, or the referenced class cannot be resolved.
     */
    private fun IrClass.extractDebuggableLoggerClass(): IrClassSymbol? {
        val annotation = getAnnotationCompat(AnnotationFqNames.DEBUGGABLE) ?: return null
        // @Debuggable(isSingleton, logger) — take the 2nd arg if present.
        val loggerArg = annotation.arguments.getOrNull(1) as? IrClassReference ?: return null
        val symbol = loggerArg.symbol as? IrClassSymbol ?: return null
        if (symbol.owner.defaultType.classFqName?.asString() == "kotlin.Nothing") return null
        return symbol
    }

    private fun objectValue(symbol: IrClassSymbol): IrGetObjectValueImpl =
        IrGetObjectValueImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = symbol.owner.defaultType,
            symbol = symbol,
        )
}
