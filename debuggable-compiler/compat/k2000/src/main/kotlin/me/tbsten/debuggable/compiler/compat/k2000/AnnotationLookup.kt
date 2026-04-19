@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package me.tbsten.debuggable.compiler.compat.k2000

import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.FqName

/**
 * Looks up an annotation by its fully-qualified name.
 *
 * Why: `org.jetbrains.kotlin.ir.util.IrUtilsKt.getAnnotation` changed return type from
 * `IrConstructorCall?` to `IrAnnotation?` in Kotlin 2.4.0-Beta1, which produces a
 * `NoSuchMethodError` when bytecode compiled against 2.3.x runs on 2.4+. Iterating
 * over [annotations] manually uses only API-stable types (`IrConstructorCall` base
 * class, annotation class FQN) so it works across all supported Kotlin versions.
 *
 * Returns null if no matching annotation is found.
 */
internal fun IrAnnotationContainer.getAnnotationCompat(name: FqName): IrConstructorCall? =
    annotations.firstOrNull { annotation ->
        annotation.symbol.owner.constructedClass.fqNameWhenAvailable == name
    }
