package example.debuggable.android.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

@Debuggable(isSingleton = true)
object LoginUseCase {
    fun execute(username: String, password: String): Boolean = username.isNotEmpty()
    fun logout() {}
}

@Debuggable(isSingleton = true)
object SessionTracker {
    @FocusDebuggable val activeSession = MutableStateFlow<String?>(null)
    @IgnoreDebuggable val cache = MutableStateFlow<Map<String, String>>(emptyMap())
}
