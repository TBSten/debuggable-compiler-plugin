package me.tbsten.debuggable.compiler.util

import org.jetbrains.kotlin.name.FqName

internal object AnnotationFqNames {
    val DEBUGGABLE = FqName("me.tbsten.debuggable.runtime.annotations.Debuggable")
    val FOCUS_DEBUGGABLE = FqName("me.tbsten.debuggable.runtime.annotations.FocusDebuggable")
    val IGNORE_DEBUGGABLE = FqName("me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable")
}
