package example.debuggable.android.userstories

import androidx.lifecycle.ViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun ViewModel.clearForTest() {
    var cls: Class<*>? = javaClass
    while (cls != null) {
        val m = cls.declaredMethods.firstOrNull { it.name == "clear" || it.name.startsWith("clear\$") }
        if (m != null) { m.isAccessible = true; m.invoke(this); return }
        cls = cls.superclass
    }
}

class ViewModelStories : UserStoryTestBase() {

    @Test fun `StateFlow changes are logged`() {
        val vm = SampleViewModel()
        Thread.sleep(50)
        vm.messages.value = listOf("hello")
        Thread.sleep(150)
        assertTrue(logger.messages.any { "messages" in it },
            "StateFlow change must be logged, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `logAction fires on method call with arguments`() {
        val vm = SampleViewModel()
        vm.sendMessage("hi")
        assertTrue(logger.messages.any { "sendMessage" in it },
            "method call must be logged, got: ${logger.messages}")
        assertTrue(logger.messages.any { "hi" in it },
            "argument must appear in log, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `no StateFlow logging after ViewModel cleared`() {
        val vm = SampleViewModel()
        Thread.sleep(50)
        vm.clearForTest()
        Thread.sleep(50)
        logger.clear()
        vm.messages.value = listOf("after-clear")
        Thread.sleep(150)
        assertFalse(logger.messages.any { "messages" in it },
            "flow change after clear must not be logged, got: ${logger.messages}")
    }

    @Test fun `plain var is logged by default`() {
        val vm = SampleViewModel()
        vm.label = "hello"
        Thread.sleep(50)
        assertTrue(logger.messages.any { "label" in it && "hello" in it },
            "plain var (no annotation) must be logged by default, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `@IgnoreDebuggable var is never logged`() {
        val vm = SampleViewModel()
        vm.authToken = "secret"
        Thread.sleep(50)
        assertFalse(logger.messages.any { "authToken" in it },
            "@IgnoreDebuggable var name must not appear in log, got: ${logger.messages}")
        assertFalse(logger.messages.any { "secret" in it },
            "@IgnoreDebuggable var value must not appear in log, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `Focus mode - @FocusDebuggable var assignment is logged`() {
        val vm = SampleViewModelFocused()
        vm.username = "daisy"
        Thread.sleep(50)
        assertTrue(logger.messages.any { "username" in it && "daisy" in it },
            "focused var assignment must be logged, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `Focus mode - unfocused StateFlow is not logged`() {
        val vm = SampleViewModelFocused()
        Thread.sleep(50)
        logger.clear()
        vm.notTracked.value = 99
        Thread.sleep(150)
        assertFalse(logger.messages.any { "notTracked" in it },
            "unfocused flow must be silent in focus mode, got: ${logger.messages}")
        vm.clearForTest()
    }

    @Test fun `per-class logger routes logs to designated logger`() {
        SampleViewModelLogger.clear()
        val vm = SampleViewModelWithCustomLogger()
        vm.login("daisy")
        assertTrue(SampleViewModelLogger.messages.any { "login" in it },
            "per-class logger must receive log, got: ${SampleViewModelLogger.messages}")
        assertTrue(logger.messages.none { "login" in it },
            "DefaultDebugLogger must NOT receive per-class log, got: ${logger.messages}")
        vm.clearForTest()
    }
}
