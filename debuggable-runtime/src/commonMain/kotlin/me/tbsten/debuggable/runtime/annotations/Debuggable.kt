package me.tbsten.debuggable.runtime.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Debuggable(val isSingleton: Boolean = false)
