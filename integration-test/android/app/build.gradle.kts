import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("me.tbsten.debuggablecompilerplugin")
}

android {
    namespace = "example.debuggable.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "example.debuggable.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Workaround for the known `debuggable-runtime-android` `.module` stdlib leak
// (tracked in `.local/tickets/bug-001-android-stdlib-leak.md`). Without this,
// Gradle upgrades `kotlin-stdlib` to the plugin's pinned version (currently
// 2.3.20) and consumer kotlinc 2.0.x / 2.1.x fails with "metadata version
// 2.3.0, expected 2.0.0". Pinning to the consumer's own Kotlin version keeps
// the metadata readable.
val consumerKotlin: String = (findProperty("integration.kotlin") as String?) ?: "2.3.20"
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib") {
            useVersion(consumerKotlin)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Versions (coroutines pinned at 1.9.0, Debuggable runtime) live in
    // `integration-test/gradle/libs.versions.toml`.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.debuggable.runtime)
}
