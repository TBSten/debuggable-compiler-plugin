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
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.COMPILER_ARTIFACT_GROUP_ID,
        artifactId = BuildConfig.COMPILER_ARTIFACT_ID,
        version = BuildConfig.COMPILER_ARTIFACT_VERSION,
    )

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
            )
        }
    }
}
