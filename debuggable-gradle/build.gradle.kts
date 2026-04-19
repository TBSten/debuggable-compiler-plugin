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
    // Keep in sync with `debuggableGroup` in the root build.gradle.kts — the Gradle
    // plugin uses this at apply-time to add `<group>:debuggable-runtime:<version>` to
    // the consumer's implementation configuration.
    buildConfigField("String", "COMPILER_ARTIFACT_GROUP_ID", "\"${rootProject.group}\"")
    buildConfigField("String", "COMPILER_ARTIFACT_ID", "\"debuggable-compiler\"")
    buildConfigField("String", "COMPILER_ARTIFACT_VERSION", "\"$debuggableVersion\"")
}

dependencies {
    // stdlib is declared explicitly because `kotlin.stdlib.default.dependency=false`
    // is set project-wide (see gradle.properties for the reason).
    implementation(libs.kotlin.stdlib)
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
