package me.tbsten.debuggable.runtime.annotations

import kotlin.reflect.KClass
import me.tbsten.debuggable.runtime.logging.DebugLogger

// SOURCE retention is intentional: the compiler plugin reads this annotation during IR
// transformation of the module that owns the annotated class. Cross-module annotation
// propagation is not supported — annotate classes in the same module as the plugin apply.
@Target(AnnotationTarget.CLASS, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Debuggable(
    val isSingleton: Boolean = false,
    /**
     * Output sink for logs emitted by this class.
     *
     * The default `Nothing::class` is a sentinel meaning "use the global default"
     * (Gradle `debuggable { defaultLogger }` if set, otherwise [me.tbsten.debuggable.runtime.logging.DefaultDebugLogger]).
     *
     * To route this class's logs to a specific sink, pass a singleton `object`
     * implementing [DebugLogger], e.g. `@Debuggable(logger = AuthLogger::class)`.
     */
    val logger: KClass<out DebugLogger> = Nothing::class,
)
