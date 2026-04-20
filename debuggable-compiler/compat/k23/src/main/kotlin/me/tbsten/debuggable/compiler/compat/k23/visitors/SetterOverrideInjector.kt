@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k23.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
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

        val valueParam = setter.parameters
            .firstOrNull { it.kind == IrParameterKind.Regular }
            ?: continue

        val propertyName = property.name.asString()

        val wrapFunction = symbolProvider.debuggableSetFunction
        val wrapParams = wrapFunction.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }

        val builder = DeclarationIrBuilder(pluginContext, irClass.symbol)

        // 1. Transform the setter body so external callers are logged.
        setter.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): org.jetbrains.kotlin.ir.expressions.IrExpression {
                if (expression.symbol != backingField.symbol) return super.visitSetField(expression)
                val originalValue = expression.value
                val wrappedValue = builder.irCall(wrapFunction).apply {
                    (typeArguments as MutableList<IrType?>)[0] = valueParam.type
                    arguments[wrapParams[0]] = builder.irString(propertyName)
                    arguments[wrapParams[1]] = originalValue
                    arguments[wrapParams[2]] = loggerResolver.resolve(irClass)
                }
                expression.value = wrappedValue
                return expression
            }
        })

        // 2. Transform each non-accessor method body to intercept intra-class
        //    setter calls. `loggerResolver.resolve()` is called per-site to avoid
        //    sharing the same IrGetObjectValueImpl node across call-sites.
        val nonAccessors = irClass.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { fn -> fn != setter && fn != property.getter }
        nonAccessors.forEach { fn ->
            fn.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): org.jetbrains.kotlin.ir.expressions.IrExpression {
                    val visited = super.visitCall(expression) as IrCall
                    if (visited.symbol != setter.symbol) return visited
                    val originalArg = visited.arguments[valueParam] ?: return visited
                    val wrappedArg = builder.irCall(wrapFunction).apply {
                        (typeArguments as MutableList<IrType?>)[0] = valueParam.type
                        arguments[wrapParams[0]] = builder.irString(propertyName)
                        arguments[wrapParams[1]] = originalArg
                        arguments[wrapParams[2]] = loggerResolver.resolve(irClass)
                    }
                    visited.arguments[valueParam] = wrappedArg
                    return visited
                }
            })
        }
    }
}
