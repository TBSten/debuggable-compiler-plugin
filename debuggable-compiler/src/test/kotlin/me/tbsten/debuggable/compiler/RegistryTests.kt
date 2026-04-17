@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DebugCleanupRegistry のライフサイクル・注入に関する RED テスト群。
 * Phase 3 (IR Injection) 実装後にすべて GREEN になることを意図している。
 */
class RegistryTests : CompilerTestBase() {

    @Test fun `AutoCloseable has hidden registry property`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyCloseable : AutoCloseable {
                override fun close() {}
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val instance = result.getInstance("MyCloseable")
        val registryField = instance.javaClass.declaredFields
            .firstOrNull { it.name.contains("debuggable") || it.name.contains("registry") }
        assertNotNull(registryField, "Expected hidden registry field on @Debuggable AutoCloseable class")
    }

    @Test fun `AutoCloseable registry is not null before close`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyCloseable : AutoCloseable {
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        val registryField = instance.javaClass.declaredFields
            .first { it.name.contains("debuggable") || it.name.contains("registry") }
            .apply { isAccessible = true }
        assertNotNull(registryField.get(instance), "Registry should be initialized before close()")
    }

    @Test fun `AutoCloseable close cancels coroutine scope`() {
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
        // After close, flow changes should not produce logs
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 99
            Thread.sleep(100)
        }
        assertFalse(output.contains("[Debuggable]"), "After close(), no log should be produced, got: $output")
    }

    @Test fun `close can be called multiple times safely`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyCloseable : AutoCloseable {
                override fun close() {}
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val instance = result.getInstance("MyCloseable")
        // Should not throw on repeated close
        instance.call("close")
        instance.call("close")
    }

    @Test fun `singleton registry is never closed by plugin`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable(isSingleton = true) object MyObj {
                val count = MutableStateFlow(0)
            }
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        // Singleton objects should use Default registry (no-op close)
        // Observation should persist after any close attempt
        val obj = result.getObject("MyObj")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (obj.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(obj) as MutableStateFlow<Int>).value = 1
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Debuggable]"), "Singleton should still track after no-op close, got: $output")
    }

    @Test fun `registry close stops all observations`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class Repo : AutoCloseable {
                val a = MutableStateFlow(0)
                val b = MutableStateFlow(0)
                val c = MutableStateFlow(0)
                override fun close() {}
            }
        """.trimIndent())
        val instance = result.getInstance("Repo")
        instance.call("close")
        val output = captureSystemOut {
            for (name in listOf("a", "b", "c")) {
                @Suppress("UNCHECKED_CAST")
                (instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as MutableStateFlow<Int>).value = 99
            }
            Thread.sleep(100)
        }
        assertFalse(output.contains("[Debuggable]"), "All observations should stop after close(), got: $output")
    }

    @Test fun `new instance has independent registry`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            import kotlinx.coroutines.flow.MutableStateFlow
            @Debuggable class Repo : AutoCloseable {
                val count = MutableStateFlow(0)
                override fun close() {}
            }
        """.trimIndent())
        val instance1 = result.getInstance("Repo")
        val instance2 = result.getInstance("Repo")
        // Close instance1 should not affect instance2
        instance1.call("close")
        val output = captureSystemOut {
            @Suppress("UNCHECKED_CAST")
            (instance2.javaClass.getDeclaredField("count").apply { isAccessible = true }.get(instance2) as MutableStateFlow<Int>).value = 42
            Thread.sleep(100)
        }
        assertTrue(output.contains("[Debuggable]"), "Instance 2 should still track after instance 1 closed, got: $output")
    }

    @Test fun `AutoCloseable original close logic still runs`() {
        val result = compile("""
            import me.tbsten.debuggable.runtime.annotations.Debuggable
            @Debuggable class MyCloseable : AutoCloseable {
                var closedCalled = false
                override fun close() { closedCalled = true }
            }
        """.trimIndent())
        val instance = result.getInstance("MyCloseable")
        instance.call("close")
        val closedField = instance.javaClass.getDeclaredField("closedCalled").apply { isAccessible = true }
        assertEquals(true, closedField.get(instance), "Original close() logic should still execute")
    }

    // TODO: ViewModel addCloseable 注入テスト (lifecycle dependency needed)
    // TODO: §3.1 patchDeclarationParents - シンボル不整合が起きないことの確認
}
