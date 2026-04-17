package me.tbsten.debuggable.runtime.annotations

// SOURCE retention — see Debuggable.kt for rationale.
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class FocusDebuggable
