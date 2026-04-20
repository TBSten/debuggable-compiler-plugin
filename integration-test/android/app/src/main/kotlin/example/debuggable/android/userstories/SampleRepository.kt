package example.debuggable.android.userstories

import kotlinx.coroutines.flow.MutableStateFlow
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable

@Debuggable(isSingleton = true)
object UserRepository {
    val users = MutableStateFlow<List<String>>(emptyList())

    fun fetchUsers(): List<String> = listOf("alice", "bob")
    fun saveUser(name: String) { users.value = users.value + name }
}

@Debuggable(isSingleton = true)
object CachedUserRepository {
    @FocusDebuggable val users = MutableStateFlow<List<String>>(emptyList())
    @IgnoreDebuggable val sensitiveData = MutableStateFlow("")
}
