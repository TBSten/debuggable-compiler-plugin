plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
    alias(libs.plugins.buildconfig)
}

val debuggablePluginId = project.property("debuggable.pluginId") as String
val debuggableVersion = project.property("debuggable.version") as String

buildConfig {
    packageName("me.tbsten.debuggable.gradle")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
    buildConfigField("String", "COMPILER_ARTIFACT_GROUP_ID", "\"me.tbsten.debuggable\"")
    buildConfigField("String", "COMPILER_ARTIFACT_ID", "\"debuggable-compiler\"")
    buildConfigField("String", "COMPILER_ARTIFACT_VERSION", "\"$debuggableVersion\"")
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        create("debuggable") {
            id = debuggablePluginId
            implementationClass = "me.tbsten.debuggable.gradle.DebuggableGradlePlugin"
        }
    }
}
