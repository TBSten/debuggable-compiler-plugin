package example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable

/**
 * KMP-wide smoke samples. These compile on every platform in
 * `integration-test/kmp-smoke` and exercise the three IR transformation
 * paths:
 *
 * - Flow wrapping on a `@Debuggable(isSingleton = true) object` (existing).
 * - `@FocusDebuggable` setter-override on plain `var` properties (new in 0.1.3).
 * - `logAction` injection on a public method.
 *
 * No platform-specific APIs are used — only commonMain types. If any compat
 * module generates IR that references a symbol not present on one of the
 * supported KMP targets, compilation fails here.
 */
@Debuggable(isSingleton = true)
object Counter {
    val count: StateFlow<Int> = MutableStateFlow(0)
    fun tick() {
        // body intentionally empty — logAction injection still wraps the call.
    }
}

@Debuggable(isSingleton = true)
object UserForm {
    @FocusDebuggable var name: String = ""
    @FocusDebuggable var age: Int = 0
    @FocusDebuggable var agreedToTerms: Boolean = false
}
