package example

import me.tbsten.debuggable.runtime.annotations.Debuggable

// Both sides carry @Debuggable: this is the stressing shape for task-121.
// Kotlin lets an actual redeclare the annotation; the plugin must tolerate it.
@Debuggable(isSingleton = true)
actual object ExpectActualCounter {
    private var counter = 0
    actual fun tick(): Int {
        counter += 1
        return counter
    }
}
