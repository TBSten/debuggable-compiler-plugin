package me.tbsten.debuggable.runtime.annotations

// SOURCE retention is intentional: the compiler plugin reads this annotation during IR
// transformation of the module that owns the annotated class. Cross-module annotation
// propagation is not supported — annotate classes in the same module as the plugin apply.
@Target(AnnotationTarget.CLASS, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Debuggable(val isSingleton: Boolean = false)
