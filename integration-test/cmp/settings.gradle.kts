rootProject.name = "debuggable-cmp-sample"

pluginManagement {
    val kotlinVersion: String = (settings.providers.gradleProperty("integration.kotlin").orNull
        ?: "2.3.20")

    // Compose Multiplatform version compatible with the given Kotlin version.
    // Reference: https://github.com/JetBrains/compose-multiplatform/blob/master/VERSIONING.md
    val computedComposeVersion: String = when {
        kotlinVersion.startsWith("2.4") -> "1.11.0-alpha01"
        kotlinVersion.startsWith("2.3") -> "1.10.3"
        kotlinVersion.startsWith("2.2.20") -> "1.9.0"
        kotlinVersion.startsWith("2.2") -> "1.8.2"
        kotlinVersion.startsWith("2.1.21") -> "1.8.2"
        kotlinVersion.startsWith("2.1") -> "1.7.3"
        kotlinVersion.startsWith("2.0.21") -> "1.7.3"
        kotlinVersion.startsWith("2.0") -> "1.6.11"
        else -> "1.10.3"
    }
    val composeVersion: String = (settings.providers.gradleProperty("integration.compose").orNull
        ?: computedComposeVersion)

    val debuggableVersion: String = (settings.providers.gradleProperty("integration.debuggable").orNull
        ?: "0.1.0")

    plugins {
        kotlin("multiplatform") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
        id("me.tbsten.debuggablecompilerplugin") version debuggableVersion
    }

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
        // For non-stable Kotlin versions (EAP / Beta / RC) and Compose dev builds
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    // `gradle/libs.versions.toml` next to this settings file is auto-discovered
    // by Gradle as the `libs` catalog — no explicit `versionCatalogs { ... }`.

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
