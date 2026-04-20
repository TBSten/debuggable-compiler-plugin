@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2000.visitors

import me.tbsten.debuggable.compiler.compat.AnnotationFqNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId

internal class DiagramCallTransformer(
    private val pluginContext: IrPluginContext,
    private val symbolProvider: SymbolProvider,
    private val loggerResolver: LoggerResolver,
) : IrElementTransformerVoid() {

    private var currentFunction: IrSimpleFunction? = null

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrSimpleFunction {
        val prev = currentFunction
        currentFunction = declaration
        val result = super.visitSimpleFunction(declaration) as IrSimpleFunction
        currentFunction = prev
        return result
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression) as? IrCall ?: return expression
        val fn = currentFunction ?: return transformed

        val receiverClass = transformed.dispatchReceiver?.type?.classOrNull?.owner ?: return transformed
        if (!receiverClass.hasAnnotation(AnnotationFqNames.DEBUGGABLE)) return transformed
        if (!receiverClass.hasDiagramEnabled()) return transformed

        val paramCount = transformed.symbol.owner.valueParameters.size
        if (paramCount == 0) return transformed

        val leafCaptures = mutableListOf<Pair<String, IrExpression>>()
        for (i in 0 until paramCount) {
            val argExpr = transformed.getValueArgument(i) ?: continue
            collectLeafNodes(argExpr).forEach { (name, expr) ->
                if (leafCaptures.none { it.first == name }) leafCaptures += name to expr
            }
        }
        if (leafCaptures.isEmpty()) return transformed

        val builder = DeclarationIrBuilder(pluginContext, fn.symbol)
        val firstArgText = transformed.getValueArgument(0)?.let { reconstructExprText(it) } ?: ""

        return builder.irBlock(resultType = transformed.type) {
            // Capture dispatch receiver in a temp to safely pass it to logDiagram
            val receiverTemp = transformed.dispatchReceiver?.let { dr ->
                irTemporary(dr, nameHint = "_d_receiver").also {
                    transformed.dispatchReceiver = irGet(it)
                }
            }

            val captureTemps: List<Pair<String, IrVariable>> = leafCaptures.map { (name, origExpr) ->
                name to irTemporary(origExpr, nameHint = "_d_$name")
            }

            val logFn = symbolProvider.logDiagramFunction.owner
            val captureCtor = symbolProvider.diagramCaptureClass.owner.constructors.single()
            val captureCtorParams = captureCtor.valueParameters

            val logCall = irCall(symbolProvider.logDiagramFunction).apply {
                putValueArgument(0, receiverTemp?.let { irGet(it) } ?: irNull())
                putValueArgument(1, irString(transformed.symbol.owner.name.asString()))
                putValueArgument(2, irString(firstArgText))
                putValueArgument(
                    3,
                    irVararg(
                        elementType = symbolProvider.diagramCaptureClass.owner.defaultType,
                        values = captureTemps.map { (name, tempVar) ->
                            irCall(captureCtor).apply {
                                putValueArgument(0, irString(name))
                                putValueArgument(1, irGet(tempVar))
                            }
                        },
                    ),
                )
                putValueArgument(logFn.valueParameters.size - 1, loggerResolver.resolve(receiverClass))
            }
            +logCall
            +transformed
        }
    }

    private fun IrClass.hasDiagramEnabled(): Boolean {
        val debuggableClassId = ClassId.fromString("me/tbsten/debuggable/runtime/annotations/Debuggable")
        val annotation = annotations.find {
            it.type.classOrNull == pluginContext.referenceClass(debuggableClassId)
        } ?: return false
        val arg = annotation.getValueArgument(3) ?: return false
        if (arg !is IrConst<*>) return false
        return try {
            @Suppress("UNCHECKED_CAST")
            arg.javaClass.getMethod("getValue").invoke(arg) as? Boolean ?: false
        } catch (_: NoSuchMethodException) { false }
    }

    private data class LeafCapture(val name: String, val expr: IrExpression)

    private fun collectLeafNodes(expr: IrExpression): List<LeafCapture> = when (expr) {
        is IrGetValue -> {
            val name = expr.symbol.owner.name.asString()
            if (name.startsWith("_d_") || name.startsWith("<")) emptyList()
            else listOf(LeafCapture(name, expr))
        }
        is IrGetField -> listOf(LeafCapture(expr.symbol.owner.name.asString(), expr))
        is IrCall -> when (expr.origin) {
            in BINARY_OPERATOR_ORIGINS -> {
                val left = expr.dispatchReceiver?.let { collectLeafNodes(it) } ?: emptyList()
                val right = expr.getValueArgument(0)?.let { collectLeafNodes(it) } ?: emptyList()
                left + right
            }
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun reconstructExprText(expr: IrExpression): String = when (expr) {
        is IrGetValue -> expr.symbol.owner.name.asString()
        is IrGetField -> expr.symbol.owner.name.asString()
        is IrConst<*> -> expr.value.toString()
        is IrCall -> {
            val opStr = OPERATOR_ORIGIN_TO_SYMBOL[expr.origin]
            val receiver = expr.dispatchReceiver
            val rightArg = expr.getValueArgument(0)
            if (opStr != null && receiver != null && rightArg != null) {
                "${reconstructExprText(receiver)} $opStr ${reconstructExprText(rightArg)}"
            } else expr.symbol.owner.name.asString() + "(...)"
        }
        else -> "?"
    }

    private companion object {
        val BINARY_OPERATOR_ORIGINS: Set<IrStatementOrigin> = setOf(
            IrStatementOrigin.PLUS, IrStatementOrigin.MINUS,
            IrStatementOrigin.MUL, IrStatementOrigin.DIV, IrStatementOrigin.PERC,
        )
        val OPERATOR_ORIGIN_TO_SYMBOL: Map<IrStatementOrigin, String> = mapOf(
            IrStatementOrigin.PLUS to "+", IrStatementOrigin.MINUS to "-",
            IrStatementOrigin.MUL to "*", IrStatementOrigin.DIV to "/",
            IrStatementOrigin.PERC to "%",
        )
    }
}
