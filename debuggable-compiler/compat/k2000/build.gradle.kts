import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Emit classfiles with `mv=[2,0,0]` so Kotlin 2.0.x / 2.1.0-2.1.10 compilers
    // accept them at load time.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val debuggablePluginId = project.property("debuggable.pluginId") as String

buildConfig {
    packageName("me.tbsten.debuggable.compiler.compat.k2000")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
}

mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin — Kotlin 2.0.0-2.0.10 impl"
        description = "IR transformation implementation for Kotlin 2.0.0 through 2.0.10 (before `builders.kt` split)"
    }
}

dependencies {
    api(project(":debuggable-compiler:compat"))
    // See sibling compat modules — `compileOnly` keeps stdlib out of published
    // POM / `.module` so it doesn't leak into consumer classpaths.
    compileOnly(libs.kotlin.stdlib)
    // Compiled against Kotlin 2.0.21's compiler API so bytecode only references symbols
    // that existed in Kotlin 2.0.x. ServiceLoader picks this impl when the running
    // compiler is 2.0.0 – 2.1.10 (before `pluginContext.messageCollector` was introduced
    // and before the new `arguments[param] = expr` / `insertExtensionReceiver` IR APIs).
    compileOnly(libs.compat.embeddable.k2000)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
