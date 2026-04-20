package example.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

// Base shape — Flow tracking + logAction + @IgnoreDebuggable.
@Debuggable
class SampleStateHolder : AutoCloseable {
    val status = MutableStateFlow("idle")
    @IgnoreDebuggable var password: String = ""

    fun updateStatus(newStatus: String) {
        status.value = newStatus
    }

    override fun close() {}
}

// Focus-mode shape — only @FocusDebuggable var is tracked; other flows are silent.
@Debuggable
class SampleStateHolderFocused : AutoCloseable {
    @FocusDebuggable var name: String = ""
    val notTracked = MutableStateFlow(0)

    override fun close() {}
}
