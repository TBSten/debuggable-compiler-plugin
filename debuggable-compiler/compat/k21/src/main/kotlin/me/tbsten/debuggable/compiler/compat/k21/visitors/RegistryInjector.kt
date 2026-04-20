@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k21.visitors

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

/** See k23's `RegistryInjector.DefinedOrigin` for the rationale — same trick applies here. */
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
 * ViewModel-path registry cleanup. See k23's `RegistryInjector.kt` for the full
 * rationale. ViewModel itself isn't AutoCloseable, but exposes
 * `addCloseable(AutoCloseable)` whose registered entries fire on `onCleared()`.
 */
internal fun injectRegistryViaAddCloseable(
    irClass: IrClass,
    registryField: IrField,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
) {
    val addCloseableSymbol = symbolProvider.viewModelAddCloseable
    if (addCloseableSymbol == null) {
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

    val insertIndex = irClass.declarations.indexOfFirst {
        it is org.jetbrains.kotlin.ir.declarations.IrProperty &&
            it.name.asString() == "\$\$debuggable_registry"
    }.let { if (it >= 0) it + 1 else irClass.declarations.size }
    irClass.declarations.add(insertIndex, initializer)
}
