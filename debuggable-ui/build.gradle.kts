import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }

    androidTarget {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    sourceSets {
        commonMain.dependencies {
            // Same pattern as debuggable-runtime — everything user-facing is
            // `compileOnly` so our published `.module` does not force a stdlib /
            // coroutines / compose version on consumers. See
            // `debuggable-runtime/build.gradle.kts` for the full rationale.
            compileOnly(libs.kotlin.stdlib)
            compileOnly(libs.kotlinx.coroutines.core)
            compileOnly(compose.runtime)
            compileOnly(compose.foundation)
            compileOnly(compose.material3)

            api(project(":debuggable-runtime"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
        }

        androidMain.dependencies {
            // AGP + KGP auto-add stdlib for Android consumers — don't leak a
            // pinned version from here (see runtime module for the incident).
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "me.tbsten.debuggable.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    pom {
        name = "Debuggable UI"
        description = "In-app Compose log viewer for the Debuggable plugin"
    }
}
