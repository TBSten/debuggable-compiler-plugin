import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

kotlin {
    compilerOptions {
        // Pin the metadata binary version to `[2,0,0]` so consumer projects on
        // Kotlin 2.0.x can load this Gradle plugin. Kotlin Gradle Plugin embeds
        // a `kotlinx-metadata-jvm` sized to its own Kotlin version — on 2.0.21
        // that library only supports metadata ≤ 2.1.0 and throws
        // "Provided metadata instance has version 2.3.0, while maximum supported
        //  version is 2.1.0" when the subplugin class is introspected.
        // Without this pin the default (= pinned compiler's 2.3.0) leaks.
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
        // Gradle 8's minimum JDK is 17. Emit Java 17 bytecode so daemons on JDK 17
        // can load the plugin, even when this module is published from CI's JDK 21.
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
