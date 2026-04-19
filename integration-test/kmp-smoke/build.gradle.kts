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

    // Native / wasmJs / js targets: KGP < 2.3.0 + Gradle 9 is broken —
    // those KGP versions reference `org.gradle.api.internal.plugins.
    // DefaultArtifactPublicationSet`, which Gradle 9 removed. Registering
    // e.g. `iosArm64()` under Gradle 9 with KGP 2.2.x and older hits
    // `NoClassDefFoundError` at configuration time. JVM registration stays
    // unaffected. Restrict the non-JVM targets to KGP 2.3.0+.
    val integrationKotlin: String = (findProperty("integration.kotlin") as String?) ?: "2.3.20"
    val kgpSupportsNative: Boolean = integrationKotlin.compareTo("2.3.0") >= 0
    if (kgpSupportsNative) {
        js { nodejs() }
        wasmJs { nodejs() }
        iosArm64()
        iosSimulatorArm64()
        macosArm64()
        linuxX64()
        mingwX64()
    }

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
