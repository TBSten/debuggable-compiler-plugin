package example.debuggable.android.userstories

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryStories : UserStoryTestBase() {

    @BeforeTest
    fun resetSingletonState() {
        UserRepository.users.value = emptyList()
        CachedUserRepository.users.value = emptyList()
        CachedUserRepository.sensitiveData.value = ""
    }

    @Test fun `logAction fires on fetch method call`() {
        UserRepository.fetchUsers()
        assertTrue(logger.messages.any { "fetchUsers" in it },
            "method call must be logged, got: ${logger.messages}")
    }

    @Test fun `logAction records saveUser call with argument`() {
        UserRepository.saveUser("alice")
        assertTrue(logger.messages.any { "saveUser" in it },
            "saveUser call must be logged, got: ${logger.messages}")
        assertTrue(logger.messages.any { "alice" in it },
            "argument must appear in log, got: ${logger.messages}")
    }

    @Test fun `StateFlow changes are logged`() {
        Thread.sleep(50)
        logger.clear()
        UserRepository.users.value = listOf("alice", "bob")
        Thread.sleep(150)
        assertTrue(logger.messages.any { "users" in it },
            "flow change must be logged, got: ${logger.messages}")
    }

    @Test fun `@FocusDebuggable StateFlow changes are logged`() {
        Thread.sleep(50)
        logger.clear()
        CachedUserRepository.users.value = listOf("alice", "bob")
        Thread.sleep(150)
        assertTrue(logger.messages.any { "users" in it },
            "focused flow change must be logged, got: ${logger.messages}")
    }

    @Test fun `@IgnoreDebuggable StateFlow is not logged`() {
        Thread.sleep(50)
        logger.clear()
        CachedUserRepository.sensitiveData.value = "secret"
        Thread.sleep(150)
        assertFalse(logger.messages.any { "sensitiveData" in it },
            "@IgnoreDebuggable flow must not be logged, got: ${logger.messages}")
        assertFalse(logger.messages.any { "secret" in it },
            "@IgnoreDebuggable value must not appear in log, got: ${logger.messages}")
    }
}
