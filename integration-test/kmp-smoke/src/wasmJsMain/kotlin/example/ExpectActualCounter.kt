package example

import me.tbsten.debuggable.runtime.annotations.Debuggable

// Identical to jvmMain actual — see that file for the task-121 rationale.
// Duplicated per target because kmp-smoke's `expect object` needs an actual
// declaration in every enabled target's source-set hierarchy.
@Debuggable(isSingleton = true)
actual object ExpectActualCounter {
    private var counter = 0
    actual fun tick(): Int {
        counter += 1
        return counter
    }
}
