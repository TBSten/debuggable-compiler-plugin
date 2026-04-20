@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k23.visitors

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

private const val CLOSE_METHOD_NAME = "close"

/**
 * Canonical `DEFINED` origin, instantiated directly instead of going through
 * `IrDeclarationOrigin.Companion.DEFINED`. The companion layout changed in a binary-
 * incompatible way in Kotlin 2.3.20 (commit `3494003c1d`), so reading
 * `DEFINED` via the companion fails to link at runtime on either side of that boundary
 * (depending on which version we were compiled against). Instantiating
 * [IrDeclarationOriginImpl] directly skips the broken accessor entirely — origin
 * equality is name-based, so any canonical `DEFINED` we compare against still matches.
 */
private val DefinedOrigin = IrDeclarationOriginImpl("DEFINED")

internal fun addRegistryProperty(
    irClass: IrClass,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
): IrField {
    val registryType = symbolProvider.debugCleanupRegistryClass.owner.defaultType

    val field = pluginContext.irFactory.buildField {
        startOffset = UNDEFINED_OFFSET
        endOffset = UNDEFINED_OFFSET
        origin = DefinedOrigin
        name = Name.identifier("\$\$debuggable_registry")
        type = registryType
        visibility = DescriptorVisibilities.PRIVATE
        isFinal = true
    }
    field.parent = irClass
    field.initializer = DeclarationIrBuilder(pluginContext, field.symbol).run {
        irExprBody(irCall(symbolProvider.debugCleanupRegistryConstructor))
    }

    val property = pluginContext.irFactory.buildProperty {
        startOffset = UNDEFINED_OFFSET
        endOffset = UNDEFINED_OFFSET
        origin = DefinedOrigin
        name = Name.identifier("\$\$debuggable_registry")
        visibility = DescriptorVisibilities.PRIVATE
        modality = Modality.FINAL
        isVar = false
    }
    property.parent = irClass
    property.backingField = field
    field.correspondingPropertySymbol = property.symbol

    irClass.declarations.add(0, property)
    return field
}

internal fun injectRegistryClose(
    irClass: IrClass,
    registryField: IrField,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
) {
    val allCloseFunctions = irClass.declarations.filterIsInstance<IrSimpleFunction>()
        .filter { fn -> fn.name.asString() == CLOSE_METHOD_NAME && fn.parameters.none { it.kind == IrParameterKind.Regular } }

    val closeFunction = allCloseFunctions.firstOrNull { !it.isFakeOverride }

    if (closeFunction == null) {
        // close() is only inherited (fake override) — registry.close() will never be called.
        if (allCloseFunctions.any { it.isFakeOverride }) {
            pluginContext.messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "@Debuggable class '${irClass.name}' inherits close() without overriding it — " +
                    "the debug registry will not be released. Override close() and call super.close().",
            )
        }
        return
    }

    val closeRegistryFn = symbolProvider.debugCleanupRegistryClass.owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .firstOrNull { it.name.asString() == CLOSE_METHOD_NAME && it.parameters.none { p -> p.kind == IrParameterKind.Regular } }
        ?.symbol ?: return

    val builder = DeclarationIrBuilder(pluginContext, closeFunction.symbol)
    val originalStatements = (closeFunction.body as? IrBlockBody)?.statements?.toList()
        ?: emptyList()

    val dispatchReceiver = closeFunction.parameters
        .firstOrNull { it.kind == IrParameterKind.DispatchReceiver }!!

    closeFunction.body = builder.irBlockBody {
        +irTry(
            type = pluginContext.irBuiltIns.unitType,
            tryResult = irBlock {
                originalStatements.forEach { +it }
            },
            catches = emptyList(),
            finallyExpression = irCall(closeRegistryFn).apply {
                insertDispatchReceiver(
                    irGetField(irGet(dispatchReceiver), registryField),
                )
            },
        )
    }
}

internal fun getDefaultRegistryExpression(
    symbolProvider: SymbolProvider,
): IrGetObjectValueImpl {
    val defaultClass = symbolProvider.debugCleanupRegistryDefaultClass
    return IrGetObjectValueImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = defaultClass.owner.defaultType,
        symbol = defaultClass,
    )
}

/**
 * For `@Debuggable class … : ViewModel()`, register the registry with the
 * ViewModel's own lifecycle via `addCloseable(AutoCloseable)`. ViewModel doesn't
 * itself implement `AutoCloseable` (it accepts closeables as constructor args
 * / via `addCloseable()`), so the standard `close()`-wrapping path doesn't
 * apply. `onCleared()` closes everything registered this way.
 *
 * Generated shape (conceptual):
 * ```
 * class MyVm : ViewModel() {
 *     private val $$debuggable_registry = DebugCleanupRegistry()
 *     init { addCloseable($$debuggable_registry) }
 * }
 * ```
 */
internal fun injectRegistryViaAddCloseable(
    irClass: IrClass,
    registryField: IrField,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
) {
    val addCloseableSymbol = symbolProvider.viewModelAddCloseable
    if (addCloseableSymbol == null) {
        // ViewModel.addCloseable(AutoCloseable) not resolvable — most likely an
        // old androidx.lifecycle on the classpath. Surface a warning so users can
        // upgrade rather than silently skipping cleanup.
        pluginContext.messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "@Debuggable on '${irClass.name}' extends androidx.lifecycle.ViewModel " +
                "but ViewModel.addCloseable(AutoCloseable) was not found on the classpath. " +
                "Upgrade androidx.lifecycle:lifecycle-viewmodel to 2.5+ for automatic cleanup.",
        )
        return
    }

    val thisReceiver = irClass.thisReceiver ?: return
    val initializer = pluginContext.irFactory.createAnonymousInitializer(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        origin = DefinedOrigin,
        symbol = org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl(),
    ).apply {
        parent = irClass
    }

    val builder = DeclarationIrBuilder(pluginContext, initializer.symbol)
    initializer.body = builder.irBlockBody {
        +irCall(addCloseableSymbol).apply {
            insertDispatchReceiver(irGet(thisReceiver))
            val regularParam = addCloseableSymbol.owner.parameters.firstOrNull {
                it.kind == IrParameterKind.Regular
            } ?: return@apply
            arguments[regularParam] = irGetField(irGet(thisReceiver), registryField)
        }
    }

    // Place the init block AFTER the registry property declaration so the field
    // is guaranteed to be initialized before we pass it to addCloseable().
    val insertIndex = irClass.declarations.indexOfFirst {
        it is org.jetbrains.kotlin.ir.declarations.IrProperty &&
            it.name.asString() == "\$\$debuggable_registry"
    }.let { if (it >= 0) it + 1 else irClass.declarations.size }
    irClass.declarations.add(insertIndex, initializer)
}
