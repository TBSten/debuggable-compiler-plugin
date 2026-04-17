@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import me.tbsten.debuggable.compiler.util.isFlow
import me.tbsten.debuggable.compiler.util.isState

internal fun injectFlowObservations(
    irClass: IrClass,
    targetProperties: List<IrProperty>,
    isSingleton: Boolean,
    registryField: IrField?,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
) {
    for (property in targetProperties) {
        val field = property.backingField ?: continue
        val existingInitializer = (field.initializer as? IrExpressionBody) ?: continue
        val returnType = property.getter?.returnType ?: continue

        val wrapFunction = when {
            returnType.isFlow() -> symbolProvider.debuggableFlowFunction
            returnType.isState() -> symbolProvider.debuggableStateFunction ?: continue
            else -> continue
        }

        val elementType = (field.type as? IrSimpleType)
            ?.arguments?.firstOrNull()
            ?.let { (it as? IrTypeProjection)?.type }
            ?: pluginContext.irBuiltIns.anyNType

        val wrapParams = wrapFunction.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }

        val builder = DeclarationIrBuilder(pluginContext, field.symbol)

        val registryExpr: IrExpression = if (isSingleton || registryField == null) {
            getDefaultRegistryExpression(symbolProvider)
        } else {
            builder.irGetField(builder.irGet(irClass.thisReceiver!!), registryField)
        }

        // Wrap the field initializer: originalInit.also { it.debuggableFlow(name, registry) }
        // In IR: block { val tmp = originalInit; tmp.debuggableFlow(...); tmp }
        field.initializer = builder.irExprBody(
            builder.irBlock(resultType = field.type) {
                val tmp = irTemporary(existingInitializer.expression, irType = field.type)
                +irCall(wrapFunction).apply {
                    (typeArguments as MutableList<IrType?>)[0] = elementType
                    insertExtensionReceiver(irGet(tmp))
                    arguments[wrapParams[0]] = irString(property.name.asString())
                    arguments[wrapParams[1]] = registryExpr
                }
                +irGet(tmp)
            }
        )
    }
}
