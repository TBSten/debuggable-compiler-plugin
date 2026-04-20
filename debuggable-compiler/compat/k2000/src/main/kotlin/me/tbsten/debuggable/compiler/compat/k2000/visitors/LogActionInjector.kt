@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2000.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody

internal fun injectLogAction(
    functions: List<IrSimpleFunction>,
    owningClass: org.jetbrains.kotlin.ir.declarations.IrClass?,
    symbolProvider: SymbolProvider,
    loggerResolver: LoggerResolver,
    pluginContext: IrPluginContext,
    captureStack: Boolean = false,
) {
    for (function in functions) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val originalStatements = (function.body as? IrBlockBody)?.statements?.toList()
            ?: continue

        val stackTraceArg = if (captureStack) {
            builder.irCall(symbolProvider.captureCallStackFunction)
        } else {
            builder.irString("")
        }

        val logCall = builder.irCall(symbolProvider.logActionFunction).apply {
            putValueArgument(0, builder.irString(function.name.asString()))
            putValueArgument(
                1,
                builder.irVararg(
                    elementType = pluginContext.irBuiltIns.anyNType,
                    values = function.valueParameters.map { builder.irGet(it) },
                ),
            )
            putValueArgument(2, loggerResolver.resolve(owningClass))
            putValueArgument(3, stackTraceArg)
        }

        function.body = builder.irBlockBody {
            +logCall
            originalStatements.forEach { +it }
        }
    }
}
