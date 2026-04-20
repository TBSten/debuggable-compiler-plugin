import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("me.tbsten.debuggablecompilerplugin")
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
                // Versions (coroutines pinned at 1.9.0, Debuggable runtime) live in
                // `integration-test/gradle/libs.versions.toml`; see that file's header
                // for the metadata-compatibility rationale behind the coroutines pin.
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.debuggable.runtime)
                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
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
