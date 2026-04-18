package me.tbsten.debuggable.compiler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gradle DSL の機能別トグル (observeFlow / logAction) のテスト。
 */
class FeatureToggleTests : CompilerTestBase() {

    @Test fun `observeFlow=false skips Flow observation but keeps method logs`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
                fun doWork() {}
            }
        """.trimIndent(), observeFlow = false)
        val obj = result.getObject("MyObj")

        val flowOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val methodOut = captureSystemOut { obj.call("doWork") }

        assertFalse(flowOut.contains("[Debuggable]"), "observeFlow=false: Flow should NOT be logged, got: $flowOut")
        assertTrue(methodOut.contains("[Debuggable]"), "observeFlow=false: method should still be logged, got: $methodOut")
    }

    @Test fun `logAction=false skips method logs but keeps Flow observation`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
                fun doWork() {}
            }
        """.trimIndent(), logAction = false)
        val obj = result.getObject("MyObj")

        val flowOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val methodOut = captureSystemOut { obj.call("doWork") }

        assertTrue(flowOut.contains("[Debuggable]"), "logAction=false: Flow should still be logged, got: $flowOut")
        assertFalse(methodOut.contains("[Debuggable]"), "logAction=false: method should NOT be logged, got: $methodOut")
    }

    @Test fun `both features disabled still compiles successfully`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
                fun doWork() {}
            }
        """.trimIndent(), observeFlow = false, logAction = false)
        val obj = result.getObject("MyObj")

        val flowOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val methodOut = captureSystemOut { obj.call("doWork") }

        assertFalse(flowOut.contains("[Debuggable]"), "Flow should NOT be logged, got: $flowOut")
        assertFalse(methodOut.contains("[Debuggable]"), "Method should NOT be logged, got: $methodOut")
    }
}
