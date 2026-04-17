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

    override fun getCompilerPluginId(): String = "me.tbsten.debuggable"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "me.tbsten.debuggable",
        artifactId = "debuggable-compiler",
        version = "1.0.0",
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(DebuggableExtension::class.java)
        return project.provider {
            listOf(SubpluginOption("enabled", extension.enabled.get().toString()))
        }
    }
}
