package me.tbsten.debuggable.compiler.compat.k23.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class SymbolProvider(private val pluginContext: IrPluginContext) {

    val debugCleanupRegistryClass: IrClassSymbol by lazy {
        pluginContext.referenceClass(
            ClassId.fromString("me/tbsten/debuggable/runtime/registry/DebugCleanupRegistry")
        ) ?: error("DebugCleanupRegistry not found on classpath")
    }

    val debugLoggerClass: IrClassSymbol by lazy {
        pluginContext.referenceClass(
            ClassId.fromString("me/tbsten/debuggable/runtime/logging/DebugLogger")
        ) ?: error("DebugLogger not found on classpath")
    }

    val defaultDebugLoggerClass: IrClassSymbol by lazy {
        pluginContext.referenceClass(
            ClassId.fromString("me/tbsten/debuggable/runtime/logging/DefaultDebugLogger")
        ) ?: error("DefaultDebugLogger not found on classpath")
    }

    fun resolveLoggerByFqn(fqn: String): IrClassSymbol? =
        ClassId.fromString(fqn.replace('.', '/')).let(pluginContext::referenceClass)

    val debugCleanupRegistryDefaultClass: IrClassSymbol by lazy {
        pluginContext.referenceClass(
            ClassId(
                FqName("me.tbsten.debuggable.runtime.registry"),
                FqName("DebugCleanupRegistry.Default"),
                false,
            )
        ) ?: error("DebugCleanupRegistry.Default not found on classpath")
    }

    val debugCleanupRegistryConstructor: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                FqName("me.tbsten.debuggable.runtime.registry"),
                Name.identifier("DebugCleanupRegistry"),
            )
        ).single()
    }

    val debuggableFlowFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                FqName("me.tbsten.debuggable.runtime.extensions"),
                Name.identifier("debuggableFlow"),
            )
        ).single()
    }

    val debuggableStateFunction: IrSimpleFunctionSymbol? by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                FqName("me.tbsten.debuggable.runtime.extensions"),
                Name.identifier("debuggableState"),
            )
        ).singleOrNull()
    }

    val logActionFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                FqName("me.tbsten.debuggable.runtime.logging"),
                Name.identifier("logAction"),
            )
        ).single()
    }

    val debuggableSetFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                FqName("me.tbsten.debuggable.runtime.extensions"),
                Name.identifier("debuggableSet"),
            )
        ).single()
    }

    /**
     * `androidx.lifecycle.ViewModel.addCloseable(AutoCloseable)` — 1-arg overload.
     * Returns null when the consumer does not have `androidx.lifecycle` on the
     * classpath (pure JVM / KMP targets) or uses a Lifecycle version older than
     * 2.5 where this overload doesn't exist. Caller is expected to warn gracefully.
     */
    val viewModelAddCloseable: IrSimpleFunctionSymbol? by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                ClassId(
                    FqName("androidx.lifecycle"),
                    Name.identifier("ViewModel"),
                ),
                Name.identifier("addCloseable"),
            )
        ).firstOrNull { sym ->
            val params = sym.owner.parameters.filter {
                it.kind == org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular
            }
            // `addCloseable(String, AutoCloseable)` vs `addCloseable(AutoCloseable)` —
            // we want the single-arg variant.
            params.size == 1
        }
    }
}
