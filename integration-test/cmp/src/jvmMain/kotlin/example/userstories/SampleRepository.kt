package example.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

// Flow tracking + logAction tests — no focus annotations.
@Debuggable(isSingleton = true)
object UserRepository {
    val users = MutableStateFlow<List<String>>(emptyList())

    fun fetchUsers(): List<String> = listOf("alice", "bob")
    fun saveUser(name: String) { users.value = users.value + name }
}

// Focus / Ignore tests — @FocusDebuggable activates focus mode.
@Debuggable(isSingleton = true)
object CachedUserRepository {
    @FocusDebuggable val users = MutableStateFlow<List<String>>(emptyList())
    @IgnoreDebuggable val sensitiveData = MutableStateFlow("")
}
