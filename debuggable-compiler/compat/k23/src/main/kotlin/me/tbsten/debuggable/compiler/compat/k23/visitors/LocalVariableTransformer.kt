@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k23.visitors

import me.tbsten.debuggable.compiler.compat.IrInjector
import me.tbsten.debuggable.compiler.compat.AnnotationFqNames
import me.tbsten.debuggable.compiler.compat.k23.util.isDebuggableTarget
import me.tbsten.debuggable.compiler.compat.k23.util.isFlow
import me.tbsten.debuggable.compiler.compat.k23.util.isState
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

/**
 * Handles `@Debuggable` on local `val` declarations inside function bodies.
 *
 * Transforms a function body like:
 * ```
 * fun doWork() {
 *     @Debuggable val tempFlow = MutableStateFlow(0)
 *     ...
 * }
 * ```
 * into (conceptually):
 * ```
 * fun doWork() {
 *     val __registry = DebugCleanupRegistry()
 *     try {
 *         val tempFlow = MutableStateFlow(0).also { it.debuggableFlow("tempFlow", __registry) }
 *         ...
 *     } finally {
 *         __registry.close()
 *     }
 * }
 * ```
 *
 * Only direct local variables inside an `IrBlockBody` are supported — nested blocks
 * (lambdas, loops, etc.) are not scanned.
 */
internal class LocalVariableTransformer(
    private val pluginContext: IrPluginContext,
    private val options: IrInjector.Options = IrInjector.Options(
        observeFlow = true,
        logAction = true,
    ),
) : IrElementTransformerVoid() {

    private val symbolProvider = SymbolProvider(pluginContext)
    private val loggerResolver = LoggerResolver(symbolProvider, options, pluginContext)

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrSimpleFunction {
        val body = declaration.body as? IrBlockBody
        if (body != null) {
            val targets = body.statements.filterIsInstance<IrVariable>().filter { variable ->
                variable.hasAnnotation(AnnotationFqNames.DEBUGGABLE) &&
                    variable.type.isDebuggableTarget() &&
                    variable.initializer != null
            }
            if (targets.isNotEmpty()) {
                rewriteBody(declaration, body, targets)
            }
        }
        return super.visitSimpleFunction(declaration) as IrSimpleFunction
    }

    private fun rewriteBody(
        function: IrSimpleFunction,
        body: IrBlockBody,
        targets: List<IrVariable>,
    ) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val originalStatements = body.statements.toList()

        val closeRegistryFn = symbolProvider.debugCleanupRegistryClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { fn ->
                fn.name.asString() == "close" && fn.parameters.none { it.kind == IrParameterKind.Regular }
            }?.symbol ?: return

        val newBody = builder.irBlockBody {
            // 1. Create a fresh DebugCleanupRegistry for this function scope.
            val registryVar = irTemporary(
                value = irCall(symbolProvider.debugCleanupRegistryConstructor),
                nameHint = "__debuggable_registry",
            )

            // 2. Rewrite each @Debuggable var's initializer so observation starts when it's assigned.
            for (target in targets) {
                wrapVariableInitializer(target, registryVar.symbol.let { registryVar })
            }

            // 3. Wrap the original statements in try-finally so cleanup always runs.
            +irTry(
                type = pluginContext.irBuiltIns.unitType,
                tryResult = irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    originalStatements.forEach { +it }
                },
                catches = emptyList(),
                finallyExpression = irCall(closeRegistryFn).apply {
                    insertDispatchReceiver(irGet(registryVar))
                },
            )
        }
        function.body = newBody
    }

    private fun IrBuilderWithScope.wrapVariableInitializer(
        variable: IrVariable,
        registryVar: IrVariable,
    ) {
        val originalInit = variable.initializer ?: return
        val wrapFunction = when {
            variable.type.isFlow() -> symbolProvider.debuggableFlowFunction
            variable.type.isState() -> symbolProvider.debuggableStateFunction ?: return
            else -> return
        }
        val elementType = (variable.type as? IrSimpleType)
            ?.arguments?.firstOrNull()
            ?.let { (it as? IrTypeProjection)?.type }
            ?: pluginContext.irBuiltIns.anyNType

        val wrapParams = wrapFunction.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }

        // Local variables don't have an owning class for per-class @Debuggable(logger=...),
        // so we resolve against null and fall back to Gradle/Default.
        val loggerExpr = loggerResolver.resolve(owningClass = null)

        // Rewrite: originalInit  ->  block { val tmp = originalInit; tmp.debuggableFlow(name, registry, logger); tmp }
        variable.initializer = irBlock(resultType = variable.type, origin = IrStatementOrigin.INITIALIZE_FIELD) {
            val tmp = irTemporary(originalInit, irType = variable.type)
            +irCall(wrapFunction).apply {
                (typeArguments as MutableList<IrType?>)[0] = elementType
                insertExtensionReceiver(irGet(tmp))
                arguments[wrapParams[0]] = irString(variable.name.asString())
                arguments[wrapParams[1]] = irGet(registryVar)
                arguments[wrapParams[2]] = loggerExpr
            }
            +irGet(tmp)
        }
    }
}
