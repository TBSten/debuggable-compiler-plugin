package example.userstories

import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class UserStoryTestBase {
    protected lateinit var logger: InMemoryLogger
    private lateinit var originalLogger: DebugLogger

    @BeforeTest
    fun setUpLogger() {
        logger = InMemoryLogger()
        originalLogger = DefaultDebugLogger.current
        DefaultDebugLogger.current = logger
    }

    @AfterTest
    fun tearDownLogger() {
        DefaultDebugLogger.current = originalLogger
    }
}
