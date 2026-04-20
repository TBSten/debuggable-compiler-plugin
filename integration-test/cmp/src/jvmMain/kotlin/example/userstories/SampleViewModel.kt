package example.userstories

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
import me.tbsten.debuggable.runtime.logging.DebugLogger

// Shared logger object used by SampleViewModelWithCustomLogger.
// Exposes collected messages so tests can assert without stdout capture.
object SampleViewModelLogger : DebugLogger {
    private val _messages = mutableListOf<String>()
    val messages: List<String> get() = _messages.toList()
    override fun log(message: String) { _messages.add(message) }
    fun clear() { _messages.clear() }
}

// Base shape — Flow tracking + logAction + @IgnoreDebuggable.
@Debuggable
class SampleViewModel : ViewModel() {
    val messages = MutableStateFlow<List<String>>(emptyList())
    val isLoading = MutableStateFlow(false)
    @IgnoreDebuggable var authToken: String = ""

    fun sendMessage(text: String) {
        messages.value = messages.value + text
    }
}

// Focus-mode shape — only @FocusDebuggable var is tracked; other flows are silent.
@Debuggable
class SampleViewModelFocused : ViewModel() {
    @FocusDebuggable var username: String = ""
    val notTracked = MutableStateFlow(0)
}

// Per-class logger shape — routes all logs to SampleViewModelLogger instead of
// DefaultDebugLogger.current.
@Debuggable(logger = SampleViewModelLogger::class)
class SampleViewModelWithCustomLogger : ViewModel() {
    fun login(user: String): Boolean = user.isNotEmpty()
}
