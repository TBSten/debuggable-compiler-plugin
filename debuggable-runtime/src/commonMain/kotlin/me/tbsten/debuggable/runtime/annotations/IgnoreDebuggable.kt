package me.tbsten.debuggable.runtime.annotations

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class IgnoreDebuggable
