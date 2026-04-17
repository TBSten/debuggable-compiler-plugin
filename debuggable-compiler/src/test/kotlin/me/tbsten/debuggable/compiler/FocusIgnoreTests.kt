package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focus / Ignore モードの RED テスト群。
 * Phase 3 (IR Injection) 実装後にすべて GREEN になることを意図している。
 * Phase 10 (Compilation Error/Warning 整備) 後にエラー系テストも GREEN になる。
 */
class FocusIgnoreTests : CompilerTestBase() {

    // ── 通常モード ───────────────────────────────────────────────────────────

    @Test fun `normal mode tracks all Flow properties`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val a = MutableStateFlow(0)
                val b = MutableStateFlow("")
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("a").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("b").apply { isAccessible = true }.get(obj) as MutableStateFlow<String>).value = "x"
            Thread.sleep(100)
        }
        assertTrue(output.contains("a") && output.contains("[Debuggable]"), "a should be tracked, got: $output")
        assertTrue(output.contains("b"), "b should be tracked, got: $output")
    }

    @Test fun `normal mode tracks all public methods`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object MyObj {
                fun methodA() {}
                fun methodB() {}
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val outputA = captureSystemOut { obj.call("methodA") }
        val outputB = captureSystemOut { obj.call("methodB") }
        assertTrue(outputA.contains("methodA"), "methodA should be tracked, got: $outputA")
        assertTrue(outputB.contains("methodB"), "methodB should be tracked, got: $outputB")
    }

    @Test fun `IgnoreDebuggable excludes Flow from normal mode`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @IgnoreDebuggable val skip = MutableStateFlow(0)
                val track = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("skip").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("skip"), "Ignored Flow should not be tracked, got: $output")
    }

    @Test fun `IgnoreDebuggable excludes method from normal mode`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            @Debuggable(isSingleton = true) object MyObj {
                @IgnoreDebuggable fun skipMe() {}
                fun trackMe() {}
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut { obj.call("skipMe") }
        assertFalse(output.contains("[Debuggable]"), "Ignored method should not be tracked, got: $output")
    }

    // ── Focus モード ──────────────────────────────────────────────────────────

    @Test fun `FocusDebuggable activates Focus mode`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                val other = MutableStateFlow(0)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        // Presence of @FocusDebuggable → Focus mode activated
    }

    @Test fun `Focus mode tracks only FocusDebuggable Flow`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                val other = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val focusedOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("focused").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val otherOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("other").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertTrue(focusedOut.contains("[Debuggable]"), "Focused Flow should be tracked, got: $focusedOut")
        assertFalse(otherOut.contains("[Debuggable]"), "Non-focused Flow should NOT be tracked in Focus mode, got: $otherOut")
    }

    @Test fun `Focus mode tracks only FocusDebuggable methods`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable fun focused() {}
                fun other() {}
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val focusedOut = captureSystemOut { obj.call("focused") }
        val otherOut = captureSystemOut { obj.call("other") }
        assertTrue(focusedOut.contains("[Debuggable]"), "Focused method should be tracked, got: $focusedOut")
        assertFalse(otherOut.contains("[Debuggable]"), "Non-focused method should NOT be tracked in Focus mode, got: $otherOut")
    }

    @Test fun `Focus mode with only property FocusDebuggable — methods not tracked`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                fun someMethod() {}
            }
        """.trimIndent())
        // getObject triggers Flow observation coroutine; drain initial log before capturing method output
        captureSystemOut { result.getObject("MyObj"); Thread.sleep(100) }
        val obj = result.getObject("MyObj")
        val methodOut = captureSystemOut { obj.call("someMethod") }
        assertFalse(methodOut.contains("[Debuggable]"), "Non-focused method should NOT be tracked in Focus mode, got: $methodOut")
    }

    @Test fun `multiple FocusDebuggable properties all tracked`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val a = MutableStateFlow(0)
                @FocusDebuggable val b = MutableStateFlow(0)
                val c = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val outputA = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("a").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val outputB = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("b").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        assertTrue(outputA.contains("[Debuggable]"), "Focused a should be tracked, got: $outputA")
        assertTrue(outputB.contains("[Debuggable]"), "Focused b should be tracked, got: $outputB")
    }

    @Test fun `Focus mode non-annotated property not tracked even with IgnoreDebuggable`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val focused = MutableStateFlow(0)
                @IgnoreDebuggable val ignored = MutableStateFlow(0)
                val plain = MutableStateFlow(0)
            }
        """.trimIndent())
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("ignored").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        assertFalse(output.contains("ignored"), "Ignored in Focus mode should not be tracked, got: $output")
    }

    // ── AutoCloseable (non-singleton) ────────────────────────────────────────

    @Test fun `AutoCloseable IgnoreDebuggable excludes Flow`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class MyCloseable : AutoCloseable {
                @IgnoreDebuggable val skip = MutableStateFlow(0)
                val track = MutableStateFlow(0)
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance.javaClass.getDeclaredField("skip").apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("skip"), "AutoCloseable: ignored Flow should not be tracked, got: $output")
    }

    @Test fun `AutoCloseable FocusDebuggable Flow tracked, other not`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class MyCloseable : AutoCloseable {
                @FocusDebuggable val focused = MutableStateFlow(0)
                val other = MutableStateFlow(0)
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val focusedOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance.javaClass.getDeclaredField("focused").apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        val otherOut = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance.javaClass.getDeclaredField("other").apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertTrue(focusedOut.contains("[Debuggable]"), "AutoCloseable: focused Flow should be tracked, got: $focusedOut")
        assertFalse(otherOut.contains("[Debuggable]"), "AutoCloseable: non-focused Flow should NOT be tracked, got: $otherOut")
    }

    @Test fun `AutoCloseable FocusDebuggable method tracked, other not`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable class MyCloseable : AutoCloseable {
                @FocusDebuggable fun focused() {}
                fun other() {}
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val focusedOut = captureSystemOut { instance.call("focused") }
        val otherOut = captureSystemOut { instance.call("other") }
        assertTrue(focusedOut.contains("[Debuggable]"), "AutoCloseable: focused method should be tracked, got: $focusedOut")
        assertFalse(otherOut.contains("[Debuggable]"), "AutoCloseable: non-focused method should NOT be tracked, got: $otherOut")
    }

    @Test fun `AutoCloseable IgnoreDebuggable excludes method`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            @Debuggable class MyCloseable : AutoCloseable {
                @IgnoreDebuggable fun skipMe() {}
                fun trackMe() {}
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val output = captureSystemOut { instance.call("skipMe") }
        assertFalse(output.contains("[Debuggable]"), "AutoCloseable: ignored method should not be tracked, got: $output")
    }

    // ── エラーケース (Phase 10 で実装) ─────────────────────────────────────────

    @Test fun `FocusDebuggable and IgnoreDebuggable on same property is compilation error`() {
        // TODO: Phase 10 (Error/Warning 整備) で有効化
        // Currently these annotations don't conflict at compile time (no error yet)
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable @IgnoreDebuggable val conflict = MutableStateFlow(0)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode,
            "Expected compilation error for @FocusDebuggable + @IgnoreDebuggable on same property")
    }

    @Test fun `Debuggable non-singleton non-lifecycle class is compilation error`() {
        // TODO: Phase 10 (Error/Warning 整備) で有効化
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyPlainClass
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode,
            "Expected error: @Debuggable on non-ViewModel/non-AutoCloseable without isSingleton=true")
    }

    @Test fun `FocusDebuggable on non-Flow property is compilation warning`() {
        // TODO: Phase 10 (Error/Warning 整備) で有効化
        // Currently compiles without warning; after Phase 10 should produce a WARNING
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object MyObj {
                @FocusDebuggable val name: String = "hello"
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        // Kotlin warning messages are prefixed with "w: " in the message output
        assertTrue(result.messages.contains("w: ") || result.messages.contains("warning") || result.messages.contains("Warning"),
            "Expected compilation warning for @FocusDebuggable on non-Flow property, got: ${result.messages}")
    }
}
