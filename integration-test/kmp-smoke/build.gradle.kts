import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin")
}

val debuggableVersion: String = (findProperty("integration.debuggable") as String?) ?: "0.1.4"
val coroutinesVersion = "1.9.0"

kotlin {
    // `metadata [2,0,0]` so this module stays Kotlin 2.0.x-consumer-compatible.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    // TODO native / wasmJs / js targets: the Kotlin Gradle plugin resolves
    //   `kotlinCompilerPluginClasspath*` for non-JVM targets with
    //   `org.gradle.jvm.environment ≠ standard-jvm`, which filters out the
    //   shared `debuggable-compiler-compat` module (published as JVM-only) and
    //   causes `ClassNotFoundException: …compat.MessageCollectorHolder` during
    //   native compile. Tracked in `.local/tickets/bug-002-native-plugin-classpath.md`.
    //   Once fixed, re-enable these targets here.

    sourceSets {
        commonMain.dependencies {
            implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:$debuggableVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
        }
    }
}
