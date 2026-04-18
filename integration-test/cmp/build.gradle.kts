import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("me.tbsten.debuggable")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                // Pinned to 1.9.0 (metadata [2,0,0]) so the sample can be built against
                // Kotlin 2.0/2.1 compilers. 1.10.x publishes metadata [2,1,0] which those
                // compilers reject (see .local/tickets/002-runtime-binary-compat-2.0-2.1.md).
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("me.tbsten.debuggable:debuggable-runtime:0.1.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "example.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "debuggable-cmp-sample"
            packageVersion = "1.0.0"
        }
    }
}
