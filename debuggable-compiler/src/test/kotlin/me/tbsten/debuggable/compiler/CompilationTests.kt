package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** プラグインがコンパイルを壊さないことを検証するテスト群。全件 GREEN を維持する。 */
class CompilationTests : CompilerTestBase() {

    @Test fun `plain function compiles`() = ok("fun main() { println(\"hi\") }")

    @Test fun `plain class compiles`() = ok("class A { val x: Int = 0 }")

    @Test fun `Debuggable singleton object compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj
    """)

    @Test fun `Debuggable AutoCloseable compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable class MyCloseable : AutoCloseable { override fun close() {} }
    """)

    @Test fun `Debuggable class with Flow property compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        @Debuggable(isSingleton = true) object MyObj { val count = MutableStateFlow(0) }
    """)

    @Test fun `Debuggable class with multiple Flow properties compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        @Debuggable(isSingleton = true) object MyObj {
            val a = MutableStateFlow(0)
            val b = MutableStateFlow("")
            val c = MutableStateFlow(false)
        }
    """)

    @Test fun `Debuggable class with non-Flow properties compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            val name: String = "hello"
            val count: Int = 0
        }
    """)

    @Test fun `Debuggable class with public methods compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            fun doA() {}
            fun doB(x: Int): String = x.toString()
        }
    """)

    @Test fun `Debuggable class with private methods compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            private fun hidden() {}
        }
    """)

    @Test fun `FocusDebuggable on Flow property compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        @Debuggable(isSingleton = true) object MyObj {
            @FocusDebuggable val focused = MutableStateFlow(0)
            val other = MutableStateFlow(1)
        }
    """)

    @Test fun `IgnoreDebuggable on Flow property compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        @Debuggable(isSingleton = true) object MyObj {
            @IgnoreDebuggable val ignored = MutableStateFlow(0)
            val tracked = MutableStateFlow(1)
        }
    """)

    @Test fun `Debuggable class with nested class compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object Outer {
            class Inner { val x = 0 }
        }
    """)

    @Test fun `Debuggable data class compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable class MyData : AutoCloseable {
            override fun close() {}
        }
    """)

    @Test fun `Debuggable class with suspend function compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            suspend fun doWork(): Int = 42
        }
    """)

    @Test fun `Debuggable class with generic return type compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            fun <T> wrap(value: T): T = value
        }
    """)

    @Test fun `Debuggable class with Flow and non-Flow properties compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        @Debuggable(isSingleton = true) object MyObj {
            val flow = MutableStateFlow(0)
            val name: String = "test"
            val count: Int = 42
        }
    """)

    @Test fun `Debuggable class with StateFlow property compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        @Debuggable(isSingleton = true) object MyObj {
            private val _count = MutableStateFlow(0)
            val count: StateFlow<Int> = _count
        }
    """)

    @Test fun `multiple Debuggable classes in same file compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object A
        @Debuggable class B : AutoCloseable { override fun close() {} }
    """)

    @Test fun `class without Debuggable alongside Debuggable class compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        class Plain { val x = 0 }
        @Debuggable(isSingleton = true) object MyObj
    """)

    @Test fun `FocusDebuggable on method compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.annotations.FocusDebuggable
        @Debuggable(isSingleton = true) object MyObj {
            @FocusDebuggable fun focused() {}
            fun other() {}
        }
    """)

    @Test fun `IgnoreDebuggable on method compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        import me.tbsten.debuggable.runtime.annotations.IgnoreDebuggable
        @Debuggable(isSingleton = true) object MyObj {
            @IgnoreDebuggable fun ignored() {}
            fun tracked() {}
        }
    """)

    @Test fun `Debuggable class with vararg method compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable(isSingleton = true) object MyObj {
            fun doWithVararg(vararg args: String): Int = args.size
        }
    """)

    @Test fun `Debuggable AutoCloseable with multiple public methods compiles`() = ok("""
        import me.tbsten.debuggable.runtime.annotations.Debuggable
        @Debuggable class Repo : AutoCloseable {
            fun fetch(): String = "data"
            fun save(item: String) {}
            fun delete(id: Int) {}
            override fun close() {}
        }
    """)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun ok(source: String) {
        val result = compile(source.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode,
            "Expected compilation success but got errors:\n${result.messages}")
    }
}
