package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Flow プロパティ追跡の RED テスト群。
 * Phase 3 (IR Injection) 実装後にすべて GREEN になることを意図している。
 * TODO: .local/test-reference.md §3.3 - sequence ビルダーの Fatal Finally Flaw ケース要追加
 */
class FlowTrackingTests : CompilerTestBase() {

    @Test fun `MutableStateFlow emission is logged`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            val countField = obj.javaClass.getDeclaredField("count")
            countField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = countField.get(obj) as MutableStateFlow<Int>
            flow.value = 1
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Debuggable]"), "Expected log on Flow emission, got: $output")
        assertTrue(output.contains("count"), "Expected property name in log, got: $output")
    }

    @Test fun `initial Flow value is logged on subscription`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val counter = MutableStateFlow(99)
            }
        """.trimIndent())
        // getObject triggers class initialization (and thus debuggableFlow calls) inside capture window
        val output = captureSystemOut {
            result.getObject("MyObj")
            Thread.sleep(100)
        }
        assertTrue(output.contains("99"), "Expected initial value in log, got: $output")
    }

    @Test fun `multiple Flow emissions are all logged`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            val flow = obj.javaClass.getDeclaredField("count").apply { isAccessible = true }
                .get(obj) as MutableStateFlow<Int>
            // Add delays between emissions so the coroutine can process each value
            flow.value = 1
            Thread.sleep(50)
            flow.value = 2
            Thread.sleep(50)
            flow.value = 3
            Thread.sleep(200)
        }
        // Should see logs for values 1, 2, 3 (and possibly 0)
        val logLines = output.lines().filter { it.contains("[Debuggable]") && it.contains("count") }
        assertTrue(logLines.size >= 3, "Expected at least 3 log entries, got: $output")
    }

    @Test fun `Flow property name appears in log`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val mySpecialFlow = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            val flow = obj.javaClass.getDeclaredField("mySpecialFlow").apply { isAccessible = true }
                .get(obj) as MutableStateFlow<Int>
            flow.value = 42
            Thread.sleep(100)
        }
        assertTrue(output.contains("mySpecialFlow"), "Expected property name 'mySpecialFlow' in log, got: $output")
    }

    @Test fun `multiple Flow properties each logged separately`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val flowA = MutableStateFlow(0)
                val flowB = MutableStateFlow("")
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("flowA").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("flowB").apply { isAccessible = true }.get(obj) as MutableStateFlow<String>).value = "x"
            Thread.sleep(100)
        }
        assertTrue(output.contains("flowA"), "Expected flowA in log, got: $output")
        assertTrue(output.contains("flowB"), "Expected flowB in log, got: $output")
    }

    @Test fun `IgnoreDebuggable Flow is NOT logged`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @IgnoreDebuggable val ignored = MutableStateFlow(0)
                val tracked = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("ignored").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("ignored"), "Ignored Flow should not be logged, got: $output")
    }

    @Test fun `FocusDebuggable Flow is logged in Focus mode`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                val other = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("focused").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        assertTrue(output.contains("focused"), "Focused Flow should be logged, got: $output")
    }

    @Test fun `non-FocusDebuggable Flow is NOT logged in Focus mode`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                val other = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("other").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("other"), "Non-focused Flow should not be logged in Focus mode, got: $output")
    }

    @Test fun `Flow observation stops after AutoCloseable close`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class MyCloseable : AutoCloseable {
                val count = MutableStateFlow(0)
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        instance.call("close")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("[Debuggable]"), "Flow should not be logged after close(), got: $output")
    }

    @Test fun `non-Flow property is NOT tracked`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object MyObj {
                val name: String = "hello"
                val count: Int = 0
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { Thread.sleep(100) }
        // Should log nothing for plain String/Int properties
        assertFalse(output.contains("name") && output.contains("[Debuggable]"),
            "Non-Flow property should not be tracked, got: $output")
    }

    @Test fun `StateFlow typed property is logged`() = runTest {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow
            import kotlinx.coroutines.flow.asStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                private val _count = MutableStateFlow(0)
                val count: StateFlow<Int> = _count.asStateFlow()
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        // StateFlow is a subtype of Flow, should be tracked; class init inside capture window to see initial value log
        val output = captureSystemOut {
            result.getObject("MyObj")
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Debuggable]"), "StateFlow should be tracked, got: $output")
    }

    // TODO: §3.3 Fatal Finally Flaw - sequence ビルダー内でのクリーンアップ未実行ケース
    // TODO: suspend Flow inside coroutine scope - cleanup timing test
    // TODO: Flow with SharedFlow type
    // TODO: custom Flow implementation
}
