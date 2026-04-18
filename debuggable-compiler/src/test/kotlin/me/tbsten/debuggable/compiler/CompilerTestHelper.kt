package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.BeforeTest

abstract class CompilerTestBase {

    @BeforeTest
    fun resetDefaultRegistryScope() {
        // DebugCleanupRegistry.Default.coroutineScope is shared across the JVM.
        // Coroutines started by a previous test's singleton object can bleed into the
        // next test's captureSystemOut window. Reset the scope before each test to
        // prevent inter-test pollution.
        runCatching {
            val defaultClass = Class.forName(
                "me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry\$Default"
            )
            val instance = defaultClass.getDeclaredField("INSTANCE").get(null)
            val resetMethod = defaultClass.getDeclaredMethod("resetScope")
            resetMethod.isAccessible = true
            resetMethod.invoke(instance)
        }
    }

    @BeforeTest
    fun resetDefaultDebugLogger() {
        // DefaultDebugLogger.current is JVM-global. Reset to Stdout between tests so
        // a test that installs a custom logger does not leak into the next test.
        DefaultDebugLogger.current = DebugLogger.Stdout
    }

    protected fun compile(
        source: String,
        pluginEnabled: Boolean = true,
        observeFlow: Boolean = true,
        logAction: Boolean = true,
        defaultLogger: String = "",
    ): JvmCompilationResult =
        compile(
            sources = arrayOf(SourceFile.kotlin("Main.kt", source)),
            pluginEnabled = pluginEnabled,
            observeFlow = observeFlow,
            logAction = logAction,
            defaultLogger = defaultLogger,
        )

    protected fun compile(
        vararg sources: SourceFile,
        pluginEnabled: Boolean = true,
        observeFlow: Boolean = true,
        logAction: Boolean = true,
        defaultLogger: String = "",
    ): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(DebuggableCompilerPluginRegistrar())
            commandLineProcessors = listOf(DebuggableCommandLineProcessor())
            pluginOptions = listOf(
                PluginOption(BuildConfig.PLUGIN_ID, "enabled", pluginEnabled.toString()),
                PluginOption(BuildConfig.PLUGIN_ID, "observeFlow", observeFlow.toString()),
                PluginOption(BuildConfig.PLUGIN_ID, "logAction", logAction.toString()),
                PluginOption(BuildConfig.PLUGIN_ID, "defaultLogger", defaultLogger),
            )
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

    protected fun captureSystemOut(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }

    protected fun JvmCompilationResult.getObject(name: String): Any =
        classLoader.loadClass(name).getDeclaredField("INSTANCE").get(null)

    protected fun JvmCompilationResult.getInstance(name: String): Any =
        classLoader.loadClass(name).getDeclaredConstructor().newInstance()

    protected fun Any.call(method: String, vararg args: Any?): Any? {
        val allMethods = javaClass.methods.toList() + javaClass.declaredMethods.toList()
        val m = allMethods.firstOrNull { it.name == method }
            ?: allMethods.first { it.name.startsWith("$method\$") }
        m.isAccessible = true
        return m.invoke(this, *args)
    }
}
