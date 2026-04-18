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
            // stdlib is compileOnly so it is NOT listed in the published Gradle
            // module metadata. Otherwise Gradle would force-upgrade consumer
            // projects to `kotlin-stdlib:2.3.20` (metadata [2,3,0]) — a drop
            // that blocks Kotlin 2.0 / 2.1 compilers. Consumers' own Kotlin
            // Gradle plugin will always add a matching stdlib for their target.
            // Version-explicit coordinate is required because
            // `kotlin.stdlib.default.dependency=false` also disables the
            // version auto-resolution of `kotlin("stdlib")`.
            compileOnly(libs.kotlin.stdlib)

            // Same reasoning as stdlib: consumers choose their own Compose
            // version. Transitive `compose.runtime` from our module would
            // upgrade them to 1.10.3 (metadata [2,1,0]).
            compileOnly(compose.runtime)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // Tests run in-process, so we need stdlib and compose.runtime on
            // the runtime classpath even though they are compileOnly for main.
            implementation(libs.kotlin.stdlib)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(compose.runtime)
        }

        androidMain.dependencies {
            // Android needs stdlib on its actual compile classpath — the
            // commonMain `compileOnly(stdlib)` does not propagate to Android's
            // releaseCompileClasspath variant. Use `implementation` on this
            // source set only; the common variant publication still keeps
            // stdlib as compileOnly so the top-level `.module` requirement
            // stays loose enough for Kotlin 2.0 / 2.1 consumers.
            implementation(libs.kotlin.stdlib)
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
