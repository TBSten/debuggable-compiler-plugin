package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import java.io.ByteArrayOutputStream
import java.io.File
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
            // The plugin-side `debuggable-compiler-compat-k*` jars need to be on the test
            // JVM's classpath (so `IrInjectorLoader` can ServiceLoader-discover a factory),
            // but they MUST NOT land on the user code compilation classpath. Each compat
            // impl carries `@Metadata(mv=[2,2,0] / [2,1,0] / [2,0,0])`, and an older
            // `kotlin-compiler-embeddable` will reject any whose metadata exceeds its own.
            // Rather than `inheritClassPath = true`, feed only what user code should see.
            classpaths = userCodeClasspath()
            messageOutputStream = System.out
            // kctfork 0.12.1 writes `args.optIn = optIn?.toTypedArray()` — null on the default
            // empty list, which trips `CommonCompilerArguments.setOptIn`'s non-null parameter
            // check introduced in Kotlin 2.4.0-Beta1. Pre-populate with an empty list so
            // `?.toTypedArray()` yields a non-null empty array.
            optIn = emptyList()
        }.compile()

    /**
     * Build the classpath the **user code** under test needs: stdlib, `debuggable-runtime`,
     * coroutines, Compose runtime, kotlin-test. Take the test JVM's full classpath and drop
     * anything we know only the plugin / compat layer / test harness should see. Using the
     * harness's own classloader keeps this version-agnostic — whichever stdlib/kotlinx jars
     * Gradle wired into `testImplementation` flow through unchanged.
     */
    private fun userCodeClasspath(): List<File> {
        val jvmClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
        return jvmClasspath.filter { entry ->
            val name = entry.name
            val path = entry.absolutePath
            when {
                // Compat jars: filename is `debuggable-compiler-compat-kXX-*.jar` regardless
                // of the underlying Gradle project path (`:debuggable-compiler:compat:kXX`).
                name.startsWith("debuggable-compiler-compat") -> false
                // Main plugin jar.
                name.startsWith("debuggable-compiler-0") -> false
                // Any local build output under `debuggable-compiler/` — covers both the
                // main plugin's `build/classes/kotlin/main` and every nested compat
                // module's `build/classes/kotlin/main` after the restructure.
                "/debuggable-compiler/" in path && "/build/" in path -> false
                // Our own test classes.
                "/build/classes/kotlin/test" in path -> false
                else -> true
            }
        }
    }

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
