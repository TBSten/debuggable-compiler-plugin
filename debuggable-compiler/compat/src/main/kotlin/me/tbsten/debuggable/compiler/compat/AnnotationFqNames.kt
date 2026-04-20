package me.tbsten.debuggable.compiler.compat

import org.jetbrains.kotlin.name.FqName

/**
 * Fully-qualified names of the runtime annotations that drive IR transformation.
 *
 * Kept on the version-agnostic [compat] module so every `debuggable-compiler-compat-kXX`
 * impl references the same string literals — adding a new annotation only requires
 * touching this one file instead of four per-version duplicates.
 *
 * Marked `public` (rather than `internal`) so the per-version compat modules, which
 * live in separate Gradle modules, can reference it. Not part of the documented SPI;
 * external consumers should not depend on these names.
 */
public object AnnotationFqNames {
    public val DEBUGGABLE: FqName = FqName("me.tbsten.debuggable.runtime.annotations.Debuggable")
    public val FOCUS_DEBUGGABLE: FqName = FqName("me.tbsten.debuggable.runtime.annotations.FocusDebuggable")
    public val IGNORE_DEBUGGABLE: FqName = FqName("me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable")
}
