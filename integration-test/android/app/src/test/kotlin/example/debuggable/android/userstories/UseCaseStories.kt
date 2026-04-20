package example.debuggable.android.userstories

import me.tbsten.debuggable.runtime.logging.CompositeLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import me.tbsten.debuggable.runtime.logging.PrefixedLogger
import me.tbsten.debuggable.runtime.logging.SilentLogger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UseCaseStories : UserStoryTestBase() {

    @BeforeTest
    fun resetSingletonState() {
        SessionTracker.activeSession.value = null
        SessionTracker.cache.value = emptyMap()
    }

    @Test fun `logAction records method call with arguments`() {
        LoginUseCase.execute("daisy", "pass")
        assertTrue(logger.messages.any { "execute" in it },
            "method call must be logged, got: ${logger.messages}")
        assertTrue(logger.messages.any { "daisy" in it },
            "username argument must appear in log, got: ${logger.messages}")
    }

    @Test fun `InMemoryLogger captures full call history`() {
        LoginUseCase.execute("daisy", "pass")
        LoginUseCase.logout()
        assertTrue(logger.messages.any { "execute" in it },
            "execute call must be recorded, got: ${logger.messages}")
        assertTrue(logger.messages.any { "logout" in it },
            "logout call must be recorded, got: ${logger.messages}")
    }

    @Test fun `SilentLogger suppresses all log output`() {
        DefaultDebugLogger.current = SilentLogger
        LoginUseCase.execute("daisy", "pass")
        assertTrue(logger.messages.isEmpty(),
            "SilentLogger must not forward to InMemoryLogger, got: ${logger.messages}")
    }

    @Test fun `CompositeLogger fans out to every child logger`() {
        val l1 = InMemoryLogger()
        val l2 = InMemoryLogger()
        DefaultDebugLogger.current = CompositeLogger(l1, l2)
        LoginUseCase.execute("bob", "pass")
        assertTrue(l1.messages.any { "execute" in it },
            "first child logger must receive log, got: ${l1.messages}")
        assertTrue(l2.messages.any { "execute" in it },
            "second child logger must receive log, got: ${l2.messages}")
    }

    @Test fun `PrefixedLogger prepends configured prefix`() {
        val inner = InMemoryLogger()
        DefaultDebugLogger.current = PrefixedLogger("[UC]", inner)
        LoginUseCase.execute("daisy", "pass")
        assertTrue(inner.messages.any { it.startsWith("[UC]") },
            "PrefixedLogger must prepend [UC], got: ${inner.messages}")
        assertTrue(inner.messages.any { "execute" in it },
            "method name must appear in prefixed log, got: ${inner.messages}")
    }

    @Test fun `@FocusDebuggable StateFlow changes are logged`() {
        Thread.sleep(50)
        logger.clear()
        SessionTracker.activeSession.value = "session-abc"
        Thread.sleep(150)
        assertTrue(logger.messages.any { "activeSession" in it },
            "focused flow change must be logged, got: ${logger.messages}")
    }

    @Test fun `@IgnoreDebuggable StateFlow is not logged`() {
        Thread.sleep(50)
        logger.clear()
        SessionTracker.cache.value = mapOf("key" to "value")
        Thread.sleep(150)
        assertFalse(logger.messages.any { "cache" in it },
            "@IgnoreDebuggable flow must not be logged, got: ${logger.messages}")
    }
}
