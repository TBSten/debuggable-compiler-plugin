@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k23.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Wrap each setter's `field = value` into `field = debuggableSet("<name>", value, logger)`
 * for the given `var` properties, so every mutation is logged.
 *
 * Intended for plain backing-field `var` properties whose type is NOT a
 * [kotlinx.coroutines.flow.Flow] / [androidx.compose.runtime.State] — those
 * already go through [injectFlowObservations]. Delegated properties (`by`)
 * have no backing field and are skipped by the caller.
 */
internal fun injectSetterOverrides(
    irClass: IrClass,
    targetVarProperties: List<IrProperty>,
    loggerResolver: LoggerResolver,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
) {
    for (property in targetVarProperties) {
        val setter = property.setter ?: continue
        if (setter.isFakeOverride) continue
        val backingField = property.backingField ?: continue

        val valueParam = setter.parameters
            .firstOrNull { it.kind == IrParameterKind.Regular }
            ?: continue

        val loggerExpr = loggerResolver.resolve(irClass)
        val propertyName = property.name.asString()

        val wrapFunction = symbolProvider.debuggableSetFunction
        val wrapParams = wrapFunction.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }
        // The default logger argument is [Regular][kind=Regular] with [hasDefaultValue]; we
        // still pass it explicitly so compilation order and classpath availability
        // stay deterministic.

        val builder = DeclarationIrBuilder(pluginContext, setter.symbol)

        setter.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): org.jetbrains.kotlin.ir.expressions.IrExpression {
                // Only rewrite writes to this property's own backing field.
                if (expression.symbol != backingField.symbol) return super.visitSetField(expression)

                val originalValue = expression.value
                val wrappedValue = builder.irCall(wrapFunction).apply {
                    (typeArguments as MutableList<IrType?>)[0] = valueParam.type
                    arguments[wrapParams[0]] = builder.irString(propertyName)
                    arguments[wrapParams[1]] = originalValue
                    arguments[wrapParams[2]] = loggerExpr
                }
                expression.value = wrappedValue
                return expression
            }
        })
    }
}

