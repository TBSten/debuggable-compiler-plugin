package example.debuggable.android.userstories

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCloseableStories : UserStoryTestBase() {

    private lateinit var holder: SampleStateHolder

    @AfterTest
    fun tearDownHolder() {
        if (::holder.isInitialized) holder.close()
    }

    @Test fun `StateFlow changes are logged`() {
        holder = SampleStateHolder()
        Thread.sleep(50)
        holder.status.value = "running"
        Thread.sleep(150)
        assertTrue(logger.messages.any { "status" in it },
            "StateFlow change must be logged, got: ${logger.messages}")
    }

    @Test fun `logAction fires on method call`() {
        holder = SampleStateHolder()
        holder.updateStatus("active")
        assertTrue(logger.messages.any { "updateStatus" in it },
            "method call must be logged, got: ${logger.messages}")
        assertTrue(logger.messages.any { "active" in it },
            "argument must appear in log, got: ${logger.messages}")
    }

    @Test fun `no StateFlow logging after close`() {
        holder = SampleStateHolder()
        Thread.sleep(50)
        holder.close()
        Thread.sleep(50)
        logger.clear()
        holder.status.value = "after-close"
        Thread.sleep(150)
        assertFalse(logger.messages.any { "status" in it },
            "flow change after close must not be logged, got: ${logger.messages}")
    }

    @Test fun `@IgnoreDebuggable var is never logged`() {
        holder = SampleStateHolder()
        holder.password = "hunter2"
        Thread.sleep(50)
        assertFalse(logger.messages.any { "password" in it },
            "@IgnoreDebuggable var must not be logged, got: ${logger.messages}")
        assertFalse(logger.messages.any { "hunter2" in it },
            "@IgnoreDebuggable value must not be logged, got: ${logger.messages}")
    }

    @Test fun `Focus mode - @FocusDebuggable var assignment is logged`() {
        val focused = SampleStateHolderFocused()
        focused.name = "daisy"
        Thread.sleep(50)
        assertTrue(logger.messages.any { "name" in it && "daisy" in it },
            "focused var assignment must be logged, got: ${logger.messages}")
        focused.close()
    }

    @Test fun `Focus mode - unfocused StateFlow is not logged`() {
        val focused = SampleStateHolderFocused()
        Thread.sleep(50)
        logger.clear()
        focused.notTracked.value = 99
        Thread.sleep(150)
        assertFalse(logger.messages.any { "notTracked" in it },
            "unfocused flow must be silent in focus mode, got: ${logger.messages}")
        focused.close()
    }
}
