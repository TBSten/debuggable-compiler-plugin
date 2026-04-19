import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin")
}

val debuggableVersion: String = (findProperty("integration.debuggable") as String?) ?: "0.1.5"
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
    js { nodejs() }
    wasmJs { nodejs() }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()
    mingwX64()

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
