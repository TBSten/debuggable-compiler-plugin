package example.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

// logAction + Logger tests — no focus annotations so logAction fires on every call.
@Debuggable(isSingleton = true)
object LoginUseCase {
    fun execute(username: String, password: String): Boolean = username.isNotEmpty()
    fun logout() {}
}

// Focus / Ignore tests — @FocusDebuggable activates focus mode on this object.
// Only activeSession changes are tracked; cache is ignored.
@Debuggable(isSingleton = true)
object SessionTracker {
    @FocusDebuggable val activeSession = MutableStateFlow<String?>(null)
    @IgnoreDebuggable val cache = MutableStateFlow<Map<String, String>>(emptyMap())
}
