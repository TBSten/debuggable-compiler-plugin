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
    // Pin the runtime library to Kotlin 2.0 language/api so it can be consumed
    // by projects on Kotlin 2.0+. Without this, classes compiled with 2.3.20
    // produce metadata that older compilers refuse to load (see smoke-test
    // failures on 2.0.21 / 2.1.21 — FirIncompatibleClassExpressionChecker).
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
    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            // Use compileOnly so consumers choose their own Compose version.
            // Without this, our published classfiles would drag the Compose runtime
            // Kotlin plugin version (currently 1.10.3, metadata [2,1,0]) into every
            // consumer, forcing their effective Compose upgrade and breaking builds
            // on Kotlin 2.0 / 2.1 compilers that reject [2,1,0] metadata.
            compileOnly(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // Tests need compose.runtime at runtime since it's only compileOnly in main.
            implementation(compose.runtime)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

    }
}

android {
    namespace = "me.tbsten.debuggable.runtime"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
// Each module only needs to set its own name and description.
mavenPublishing {
    pom {
        name = "Debuggable Runtime"
        description = "Runtime library consumed by @Debuggable-annotated code (KMP)"
    }
}
