package me.tbsten.debuggable.compiler.compat

import java.util.ServiceLoader

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
    fun load(
        classLoader: ClassLoader = IrInjector::class.java.classLoader,
    ): IrInjector {
        // Using the interface's own classloader (rather than the thread context) ensures we
        // see the per-version impl JARs that the Kotlin compiler loaded alongside our plugin.
        val factories = ServiceLoader.load(IrInjector.Factory::class.java, classLoader)
            .toList()
        require(factories.isNotEmpty()) {
            "No IrInjector.Factory found on classpath. Check that one of the " +
                "`debuggable-compiler-compat-kXX` modules is on the runtime classpath."
        }

        val currentVersion = detectKotlinVersion(classLoader)
            ?.let { SimpleKotlinVersion.parse(it) }

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

        return factory.create()
    }

    private fun detectKotlinVersion(classLoader: ClassLoader): String? {
        val stream = classLoader.getResourceAsStream("META-INF/compiler.version")
            ?: return null
        return stream.bufferedReader().use { it.readText().trim() }
            .takeUnless { it.isBlank() }
    }
}
