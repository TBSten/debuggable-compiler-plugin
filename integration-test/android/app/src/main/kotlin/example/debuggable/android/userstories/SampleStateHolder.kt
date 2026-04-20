package example.debuggable.android.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

@Debuggable
class SampleStateHolder : AutoCloseable {
    val status = MutableStateFlow("idle")
    @IgnoreDebuggable var password: String = ""

    fun updateStatus(newStatus: String) {
        status.value = newStatus
    }

    override fun close() {}
}

@Debuggable
class SampleStateHolderFocused : AutoCloseable {
    @FocusDebuggable var name: String = ""
    val notTracked = MutableStateFlow(0)

    override fun close() {}
}
