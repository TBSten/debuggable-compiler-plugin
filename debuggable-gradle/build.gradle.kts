plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
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
            displayName = "Debuggable Gradle Plugin"
            description = "Applies the Debuggable compiler plugin to Kotlin modules"
        }
    }
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
mavenPublishing {
    pom {
        name = "Debuggable Gradle Plugin"
        description = "Gradle plugin that applies the Debuggable compiler plugin"
    }
}
