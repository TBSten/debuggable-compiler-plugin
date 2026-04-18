package me.tbsten.debuggable.compiler.compat.k2020.util

import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName

private val STATE_FQDNS = setOf(
    "androidx.compose.runtime.State",
    "androidx.compose.runtime.MutableState",
)

private val FLOW_FQDNS = setOf(
    "kotlinx.coroutines.flow.Flow",
    "kotlinx.coroutines.flow.StateFlow",
    "kotlinx.coroutines.flow.MutableStateFlow",
)

internal fun IrType.isState(): Boolean = classFqName?.asString() in STATE_FQDNS

internal fun IrType.isFlow(): Boolean = classFqName?.asString() in FLOW_FQDNS

internal fun IrType.isDebuggableTarget(): Boolean = isState() || isFlow()
