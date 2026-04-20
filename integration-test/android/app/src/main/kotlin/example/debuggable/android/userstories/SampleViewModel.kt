package example.debuggable.android.userstories

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
import me.tbsten.debuggable.runtime.logging.DebugLogger

object SampleViewModelLogger : DebugLogger {
    private val _messages = mutableListOf<String>()
    val messages: List<String> get() = _messages.toList()
    override fun log(message: String) { _messages.add(message) }
    fun clear() { _messages.clear() }
}

@Debuggable
class SampleViewModel : ViewModel() {
    val messages = MutableStateFlow<List<String>>(emptyList())
    val isLoading = MutableStateFlow(false)
    var label: String = ""
    @IgnoreDebuggable var authToken: String = ""

    fun sendMessage(text: String) {
        messages.value = messages.value + text
    }
}

@Debuggable
class SampleViewModelFocused : ViewModel() {
    @FocusDebuggable var username: String = ""
    val notTracked = MutableStateFlow(0)
}

@Debuggable(logger = SampleViewModelLogger::class)
class SampleViewModelWithCustomLogger : ViewModel() {
    fun login(user: String): Boolean = user.isNotEmpty()
}
