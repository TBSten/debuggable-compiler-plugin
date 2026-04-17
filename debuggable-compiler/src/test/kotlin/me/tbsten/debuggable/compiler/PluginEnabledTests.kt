package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Gradle plugin の enabled オプションのテスト。
 * enabled=false のとき IR 変換が完全にスキップされることを検証する。
 */
class PluginEnabledTests : CompilerTestBase() {

    @Test fun `plugin disabled — compilation still succeeds`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
            }
        """.trimIndent(), pluginEnabled = false)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test fun `plugin disabled — Flow changes are not logged`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
            }
        """.trimIndent(), pluginEnabled = false)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("[Debuggable]"), "Plugin disabled: should produce no logs, got: $output")
    }

    @Test fun `plugin disabled — methods are not logged`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object MyObj {
                fun doWork() {}
            }
        """.trimIndent(), pluginEnabled = false)
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("doWork") }
        assertFalse(output.contains("[Debuggable]"), "Plugin disabled: method should not be logged, got: $output")
    }

    @Test fun `plugin disabled — non-singleton non-AutoCloseable class compiles without error`() {
        // When disabled, the validator should also be skipped
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyPlainClass
        """.trimIndent(), pluginEnabled = false)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Plugin disabled: @Debuggable on plain class should not error, got: ${result.messages}")
    }

    @Test fun `plugin enabled by default`() {
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
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        assert(output.contains("[Debuggable]")) { "Plugin enabled by default: should log, got: $output" }
    }
}
