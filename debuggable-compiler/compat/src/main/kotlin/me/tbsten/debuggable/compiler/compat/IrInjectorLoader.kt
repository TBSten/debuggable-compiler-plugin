package me.tbsten.debuggable.compiler.compat

import java.util.ServiceLoader
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Discovers all available [IrInjector.Factory] instances via [ServiceLoader] and
 * picks the one whose [IrInjector.Factory.minVersion] is the highest that is still
 * `<=` the Kotlin compiler version on the classpath.
 *
 * The current compiler version is read from `META-INF/compiler.version` (a resource
 * that ships with every `kotlin-compiler-embeddable`). If the resource is missing,
 * the highest-versioned factory wins as a fallback.
 */
object IrInjectorLoader {
    /** Set to `true` to print which factories were discovered / chosen to stderr. */
    private val DEBUG: Boolean = System.getProperty("debuggable.compat.debug") == "true"

    fun load(
        classLoader: ClassLoader = IrInjector::class.java.classLoader,
    ): IrInjector {
        // Using the interface's own classloader (rather than the thread context) ensures we
        // see the per-version impl JARs that the Kotlin compiler loaded alongside our plugin.
        //
        // Each `debuggable-compiler-compat-kXX` impl references APIs from its target Kotlin
        // version; factories that can't be linked on the current runtime throw
        // `NoClassDefFoundError` / `ServiceConfigurationError` during iteration.  Swallow
        // those and keep the ones that load cleanly.
        val rawIterator = ServiceLoader.load(IrInjector.Factory::class.java, classLoader).iterator()
        val factories = mutableListOf<IrInjector.Factory>()
        while (true) {
            val factory = try {
                if (!rawIterator.hasNext()) break
                rawIterator.next()
            } catch (t: Throwable) {
                if (DEBUG) System.err.println("[Debuggable compat] factory skipped: ${t.javaClass.simpleName}: ${t.message}")
                continue
            }
            if (DEBUG) System.err.println("[Debuggable compat] factory found: ${factory.javaClass.name} (minVersion=${factory.minVersion})")
            factories += factory
        }
        require(factories.isNotEmpty()) {
            "No IrInjector.Factory found on classpath. Check that one of the " +
                "`debuggable-compiler-compat-kXX` modules is on the runtime classpath."
        }

        val rawVersion = detectKotlinVersion(classLoader)
        val currentVersion = rawVersion?.let { SimpleKotlinVersion.parse(it) }

        // Pick the factory with highest minVersion that is still <= current version.
        val chosen = if (currentVersion != null) {
            factories
                .map { it to SimpleKotlinVersion.parse(it.minVersion) }
                .filter { (_, v) -> v <= currentVersion }
                .maxByOrNull { (_, v) -> v }
                ?.first
        } else null

        val factory = chosen ?: factories
            .maxByOrNull { SimpleKotlinVersion.parse(it.minVersion) }!!

        reportSelection(factory, rawVersion, chosen == null)

        return factory.create()
    }

    // Emits a single INFO-level line so CI logs (and `--info` builds) can show
    // which compat impl was picked for the running Kotlin compiler. Silent
    // selection is the historical cause of several "why didn't the plugin run"
    // bug reports.
    private fun reportSelection(
        factory: IrInjector.Factory,
        currentVersion: String?,
        fellBackToHighest: Boolean,
    ) {
        val msg = buildString {
            append("[Debuggable] compat impl selected: ")
            append(factory.javaClass.name)
            append(" (minVersion=").append(factory.minVersion).append(")")
            if (currentVersion != null) {
                append(" for Kotlin ").append(currentVersion)
            } else {
                append(" (Kotlin version not detected)")
            }
            if (fellBackToHighest) {
                append(" [fallback: highest minVersion]")
            }
        }
        MessageCollectorHolder.get()?.report(CompilerMessageSeverity.INFO, msg)
            ?: if (DEBUG) System.err.println(msg) else Unit
    }

    private fun detectKotlinVersion(classLoader: ClassLoader): String? {
        val stream = classLoader.getResourceAsStream("META-INF/compiler.version")
            ?: return null
        return stream.bufferedReader().use { it.readText().trim() }
            .takeUnless { it.isBlank() }
    }
}
