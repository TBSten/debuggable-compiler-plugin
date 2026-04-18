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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

private const val CLOSE_METHOD_NAME = "close"

internal fun addRegistryProperty(
    irClass: IrClass,
    symbolProvider: SymbolProvider,
    pluginContext: IrPluginContext,
): IrField {
    val registryType = symbolProvider.debugCleanupRegistryClass.owner.defaultType

    val field = pluginContext.irFactory.buildField {
        startOffset = UNDEFINED_OFFSET
        endOffset = UNDEFINED_OFFSET
        origin = IrDeclarationOrigin.DEFINED
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
        origin = IrDeclarationOrigin.DEFINED
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
