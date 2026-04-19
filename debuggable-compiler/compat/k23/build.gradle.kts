import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    api(project(":debuggable-compiler:compat"))
    // `compileOnly` (not `implementation`) — stdlib is always on kotlinc's
    // compiler-plugin classloader at load time. Declaring it as `implementation`
    // leaks `kotlin-stdlib:<pinned>` into this module's POM / `.module`, which
    // Gradle propagates into consumer compile classpaths via KGP and breaks
    // Kotlin 2.0.x / 2.1.x consumers ("metadata version 2.3.0, expected 2.0.0").
    compileOnly(libs.kotlin.stdlib)
    // Pinned to 2.2.0 (this impl's minVersion) so the resulting bytecode only references
    // symbols that already existed in 2.2.0 — e.g. the pre-2.3.20 `IrDeclarationOrigin`
    // companion layout (see KT commit `3494003c1d`, which renamed the companion accessor
    // and would otherwise link-fail on 2.2.0 – 2.3.10 runtimes). Version defined in
    // `gradle/libs.versions.toml` as `kotlin-compiler-embeddable-k23`.
    compileOnly(libs.compat.embeddable.k23)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
