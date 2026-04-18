@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody

internal fun injectLogAction(
    functions: List<IrSimpleFunction>,
    owningClass: org.jetbrains.kotlin.ir.declarations.IrClass?,
    symbolProvider: SymbolProvider,
    loggerResolver: LoggerResolver,
    pluginContext: IrPluginContext,
) {
    val logActionParams = symbolProvider.logActionFunction.owner.parameters
        .filter { it.kind == IrParameterKind.Regular }

    for (function in functions) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val originalStatements = (function.body as? IrBlockBody)?.statements?.toList()
            ?: continue

        val logCall = builder.irCall(symbolProvider.logActionFunction).apply {
            arguments[logActionParams[0]] = builder.irString(function.name.asString())
            arguments[logActionParams[1]] = builder.irVararg(
                elementType = pluginContext.irBuiltIns.anyNType,
                values = function.parameters.filter { it.kind == IrParameterKind.Regular }
                    .map { builder.irGet(it) },
            )
            arguments[logActionParams[2]] = loggerResolver.resolve(owningClass)
        }

        function.body = builder.irBlockBody {
            +logCall
            originalStatements.forEach { +it }
        }
    }
}
