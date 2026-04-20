@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2020.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * k2020 (Kotlin 2.0.20 – 2.1.10): same shape as the k23 injector but uses the
 * pre-2.1.20 IR builder APIs (`putValueArgument` / `putTypeArgument`).
 *
 * Two injection sites are needed:
 * 1. The setter body — covers any external caller that invokes the setter directly.
 * 2. Each non-accessor method body — covers intra-class assignments. In K2 IR,
 *    `count = expr` inside an object/class method compiles to `IrCall(setter, expr)`
 *    at the IR plugin phase; the JVM backend later lowers this to a direct PUTFIELD
 *    that bypasses the setter body. Intercepting the setter call and wrapping its
 *    VALUE argument with `debuggableSet` ensures the log fires regardless.
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

        val propertyName = property.name.asString()
        val wrapFunction = symbolProvider.debuggableSetFunction
        val builder = DeclarationIrBuilder(pluginContext, irClass.symbol)

        // 1. Transform the setter body so external callers are logged.
        setter.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                if (expression.symbol != backingField.symbol) return super.visitSetField(expression)
                val originalValue = expression.value
                val wrappedValue = builder.irCall(wrapFunction).apply {
                    putTypeArgument(0, valueParam.type)
                    putValueArgument(0, builder.irString(propertyName))
                    putValueArgument(1, originalValue)
                    putValueArgument(2, loggerResolver.resolve(irClass))
                }
                expression.value = wrappedValue
                return expression
            }
        })

        // 2. Transform each non-accessor method body to intercept intra-class
        //    setter calls. `loggerResolver.resolve()` is called per-site to avoid
        //    sharing the same IrGetObjectValueImpl node across call-sites.
        irClass.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { fn -> fn != setter && fn != property.getter }
            .forEach { fn ->
                fn.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitCall(expression: IrCall): IrExpression {
                        val visited = super.visitCall(expression) as IrCall
                        if (visited.symbol != setter.symbol) return visited
                        val originalArg = visited.getValueArgument(0) ?: return visited
                        val wrappedArg = builder.irCall(wrapFunction).apply {
                            putTypeArgument(0, valueParam.type)
                            putValueArgument(0, builder.irString(propertyName))
                            putValueArgument(1, originalArg)
                            putValueArgument(2, loggerResolver.resolve(irClass))
                        }
                        visited.putValueArgument(0, wrappedArg)
                        return visited
                    }
                })
            }
    }
}
