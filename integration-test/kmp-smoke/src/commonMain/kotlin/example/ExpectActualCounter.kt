package example

import me.tbsten.debuggable.runtime.annotations.Debuggable

/**
 * task-121 scenario — `@Debuggable` on both expect and actual declarations.
 *
 * The expect side compiles to klib metadata (no method bodies). The actual
 * side is the one that gets IR transformation applied. If the plugin
 * accidentally "doubles up" (transforms both sides, or transforms the expect
 * declaration in a way that combines with the actual's injection), the
 * emitted logs would fire twice per call.
 *
 * The smoke test on the jvm target asserts exactly one log line per
 * `tick()` call.
 */
@Debuggable(isSingleton = true)
expect object ExpectActualCounter {
    fun tick(): Int
}
