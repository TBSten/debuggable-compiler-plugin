@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2020.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * k2020 (Kotlin 2.0.20 – 2.1.10): same shape as the k23 injector but uses the
 * pre-2.1.20 IR builder APIs (`putValueArgument` / `putTypeArgument`). No
 * `IrParameterKind.Regular` filtering — the setter's `value` parameter is
 * always the first and only regular param.
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
        val valueParam = setter.valueParameters.firstOrNull() ?: continue

        val loggerExpr = loggerResolver.resolve(irClass)
        val propertyName = property.name.asString()
        val wrapFunction = symbolProvider.debuggableSetFunction
        val builder = DeclarationIrBuilder(pluginContext, setter.symbol)

        setter.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                if (expression.symbol != backingField.symbol) return super.visitSetField(expression)

                val originalValue = expression.value
                val wrappedValue = builder.irCall(wrapFunction).apply {
                    putTypeArgument(0, valueParam.type)
                    putValueArgument(0, builder.irString(propertyName))
                    putValueArgument(1, originalValue)
                    putValueArgument(2, loggerExpr)
                }
                expression.value = wrappedValue
                return expression
            }
        })
    }
}
