import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Emit classfiles with `mv=[2,2,0]` so Kotlin 2.2.0+ compilers accept them
    // (this impl's `minVersion`). 2.3.20 API calls baked into the bytecode still
    // require 2.2.0+ at load time, so a 2.2 metadata stamp is the widest we can
    // claim without misleading older compilers.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
}

mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin — Kotlin 2.2+ impl"
        description = "IR transformation implementation for Kotlin 2.2.0 through 2.4.0-Beta1"
    }
}

val debuggablePluginId = project.property("debuggable.pluginId") as String

buildConfig {
    packageName("me.tbsten.debuggable.compiler.compat.k23")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
}

dependencies {
    api(project(":debuggable-compiler-compat"))
    implementation(libs.kotlin.stdlib)
    // This impl is compiled against Kotlin 2.3.20's compiler API. The same bytecode is
    // also loaded at runtime on 2.2.x because those versions retain the APIs used here.
    compileOnly(libs.kotlin.compiler.embeddable)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
