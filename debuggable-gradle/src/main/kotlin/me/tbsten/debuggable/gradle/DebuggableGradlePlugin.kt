package me.tbsten.debuggable.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class DebuggableGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("debuggable", DebuggableExtension::class.java)

        // Fail-fast guardrail: if no Kotlin plugin is applied by the time the
        // project is fully configured, `applyToCompilation` is never called and
        // the user sees a silent no-op. Warn them instead.
        target.afterEvaluate {
            val hasKotlin = KOTLIN_PLUGIN_IDS.any { target.pluginManager.hasPlugin(it) }
            if (!hasKotlin) {
                target.logger.warn(
                    "[Debuggable] `${target.path}` applies `me.tbsten.debuggablecompilerplugin` " +
                        "but no Kotlin plugin (kotlin-jvm / kotlin-multiplatform / kotlin-android) " +
                        "is applied. The compiler plugin will have no effect. " +
                        "Apply the Debuggable plugin on the same module as the Kotlin plugin — " +
                        "note that applying it only on the root project does NOT propagate to subprojects.",
                )
            }
        }
    }

    private companion object {
        private val KOTLIN_PLUGIN_IDS = listOf(
            "org.jetbrains.kotlin.jvm",
            "org.jetbrains.kotlin.multiplatform",
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.js",
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.COMPILER_ARTIFACT_GROUP_ID,
        artifactId = BuildConfig.COMPILER_ARTIFACT_ID,
        version = BuildConfig.COMPILER_ARTIFACT_VERSION,
    )

    // Native (Kotlin/Native) and Wasm targets use a separate compiler-plugin classpath
    // whose variant attributes (`org.gradle.jvm.environment=non-jvm`) would otherwise
    // filter out our JVM-only transitive `debuggable-compiler-compat*` deps — causing
    // `ClassNotFoundException: me.tbsten.debuggable.compiler.compat.…` at compile time.
    // Returning the same artifact coordinate here tells KGP to reuse the JVM resolution
    // for native, which resolves the full plugin + compat classpath correctly.
    override fun getPluginArtifactForNative(): SubpluginArtifact = getPluginArtifact()

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(DebuggableExtension::class.java)
        return project.provider {
            listOf(
                SubpluginOption("enabled", extension.enabled.get().toString()),
                SubpluginOption("observeFlow", extension.observeFlow.get().toString()),
                SubpluginOption("logAction", extension.logAction.get().toString()),
                SubpluginOption("defaultLogger", extension.defaultLogger.get()),
            )
        }
    }
}
