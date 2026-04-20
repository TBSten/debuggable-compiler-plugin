@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k23.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody

internal fun injectLogAction(
    functions: List<IrSimpleFunction>,
    owningClass: IrClass?,
    symbolProvider: SymbolProvider,
    loggerResolver: LoggerResolver,
    pluginContext: IrPluginContext,
    captureStack: Boolean = false,
) {
    val logActionParams = symbolProvider.logActionFunction.owner.parameters
        .filter { it.kind == IrParameterKind.Regular }

    for (function in functions) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val originalStatements = (function.body as? IrBlockBody)?.statements?.toList()
            ?: continue

        val stackTraceArg = if (captureStack) {
            builder.irCall(symbolProvider.captureCallStackFunction)
        } else {
            builder.irString("")
        }

        val dispatchReceiverParam = function.parameters
            .firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
        val receiverExpr = dispatchReceiverParam?.let { builder.irGet(it) } ?: builder.irNull()

        val logCall = builder.irCall(symbolProvider.logActionFunction).apply {
            arguments[logActionParams[0]] = receiverExpr
            arguments[logActionParams[1]] = builder.irString(function.name.asString())
            arguments[logActionParams[2]] = builder.irVararg(
                elementType = pluginContext.irBuiltIns.anyNType,
                values = function.parameters.filter { it.kind == IrParameterKind.Regular }
                    .map { builder.irGet(it) },
            )
            arguments[logActionParams[3]] = loggerResolver.resolve(owningClass)
            arguments[logActionParams[4]] = stackTraceArg
        }

        function.body = builder.irBlockBody {
            +logCall
            originalStatements.forEach { +it }
        }
    }
}
