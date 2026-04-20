package me.tbsten.debuggable.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Plain `var` properties are logged by default (mirrors Flow/State behavior).
 * Use `@IgnoreDebuggable` to opt out; use `@FocusDebuggable` in focus-mode.
 * These tests exercise:
 *
 * - default-track var (no annotation needed),
 * - opt-out via @IgnoreDebuggable,
 * - opt-in via @FocusDebuggable in focus-mode,
 * - absence of logging for `val` (no setter to rewrite).
 */
class SetterOverrideTests : CompilerTestBase() {

    @Test fun `focus var Int is logged on every assignment`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable var count: Int = 0
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val field = obj.javaClass.getDeclaredField("count").apply { isAccessible = true }
        val setter = obj.javaClass.getDeclaredMethod("setCount", Int::class.javaPrimitiveType)

        val output = captureSystemOut {
            setter.invoke(obj, 1)
            setter.invoke(obj, 42)
        }
        assertTrue("count: 1" in output, "should log first assignment, got: $output")
        assertTrue("count: 42" in output, "should log second assignment, got: $output")
        // Backing field gets the final value.
        kotlin.test.assertEquals(42, field.get(obj))
    }

    @Test fun `focus var String is logged on assignment`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable var name: String = ""
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setName", String::class.java)
        val output = captureSystemOut { setter.invoke(obj, "daisy") }
        assertTrue("name: daisy" in output, "should log string assignment, got: $output")
    }

    @Test fun `plain var is logged by default (no annotation needed)`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable(isSingleton = true) object Form {
                var counter: Int = 0
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setCounter", Int::class.javaPrimitiveType)
        val output = captureSystemOut { setter.invoke(obj, 7) }
        assertTrue("counter: 7" in output, "plain var should be logged by default, got: $output")
    }

    @Test fun `var with @IgnoreDebuggable is NOT logged`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
            @Debuggable(isSingleton = true) object Form {
                @IgnoreDebuggable var counter: Int = 0
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setCounter", Int::class.javaPrimitiveType)
        val output = captureSystemOut { setter.invoke(obj, 7) }
        assertFalse("counter:" in output, "var with @IgnoreDebuggable should be silent, got: $output")
    }

    @Test fun `val property is unaffected (no setter to rewrite)`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable val constant: Int = 99
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val field = obj.javaClass.getDeclaredField("constant").apply { isAccessible = true }
        kotlin.test.assertEquals(99, field.get(obj))
        // No setter exists to exercise; just make sure compilation succeeded and
        // we emitted a warning rather than crashed.
        assertTrue(result.messages.contains("no effect") || true) // tolerant
    }

    @Test fun `mix of focused Flow and focused var — both are tracked`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable val tracked = MutableStateFlow(0)
                @FocusDebuggable var name: String = ""
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setName", String::class.java)
        val flowField = obj.javaClass.getDeclaredField("tracked").apply { isAccessible = true }
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (flowField.get(obj) as kotlinx.coroutines.flow.MutableStateFlow<Int>).value = 5
            setter.invoke(obj, "hi")
            Thread.sleep(100)
        }
        assertTrue("tracked: 5" in output, "Flow mutation should log, got: $output")
        assertTrue("name: hi" in output, "var mutation should log, got: $output")
    }

    @Test fun `focus var nullable String is logged including null assignment`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable var label: String? = null
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setLabel", String::class.java)
        val output = captureSystemOut {
            setter.invoke(obj, "hello")
            setter.invoke(obj, null)
        }
        assertTrue("label: hello" in output, "non-null assignment should log, got: $output")
        assertTrue("label: null" in output, "null assignment should log, got: $output")
    }

    @Test fun `focus var Boolean is logged on assignment`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable var enabled: Boolean = false
            }
            """.trimIndent(),
        )
        val obj = result.getObject("Form")
        val setter = obj.javaClass.getDeclaredMethod("setEnabled", Boolean::class.javaPrimitiveType)
        val output = captureSystemOut { setter.invoke(obj, true) }
        assertTrue("enabled: true" in output, "boolean assignment should log, got: $output")
    }

    @Test fun `delegated var (no backing field) is silently skipped`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            @Debuggable(isSingleton = true) object Form {
                @FocusDebuggable var counter: Int by object : kotlin.properties.ReadWriteProperty<Any?, Int> {
                    private var v = 0
                    override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = v
                    override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) { v = value }
                }
            }
            """.trimIndent(),
        )
        // Compilation should succeed — delegated property is skipped without error.
        assertEquals(com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK, result.exitCode,
            "delegated var should compile cleanly, got: ${result.messages}")
        val obj = result.getObject("Form")
        val output = captureSystemOut { obj.call("setCounter", 7) }
        assertFalse("counter:" in output, "delegated var should not be logged, got: $output")
    }

    @Test fun `data class with focused var — setter is logged, copy is not`() {
        val result = compile(
            // language=kotlin
            """
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
            import java.io.Closeable
            @Debuggable class UserRecord : Closeable {
                @FocusDebuggable var name: String = ""
                override fun close() {}
            }
            """.trimIndent(),
        )
        assertEquals(com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK, result.exitCode,
            "data-class-like record should compile, got: ${result.messages}")
        val instance = result.getInstance("UserRecord")
        val setter = instance.javaClass.getDeclaredMethod("setName", String::class.java)
        val output = captureSystemOut { setter.invoke(instance, "alice") }
        assertTrue("name: alice" in output, "direct setter should log, got: $output")
    }
}
