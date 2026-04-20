rootProject.name = "debuggable-kmp-smoke"

pluginManagement {
    val kotlinVersion: String = (settings.providers.gradleProperty("integration.kotlin").orNull
        ?: "2.3.20")

    // Canonical default is `debuggable.version` in this sample's
    // `gradle.properties` (kept in sync with the repo root via
    // `/bump-library-version`). `-Pintegration.debuggable=X.Y.Z` still wins
    // when invoked explicitly (e.g. from scripts/smoke-test.sh).
    val debuggableVersion: String = settings.providers.gradleProperty("integration.debuggable").orNull
        ?: settings.providers.gradleProperty("debuggable.version").orNull
        ?: error("debuggable.version missing — set it in gradle.properties or pass -Pintegration.debuggable=…")

    plugins {
        kotlin("multiplatform") version kotlinVersion
        id("me.tbsten.debuggablecompilerplugin") version debuggableVersion
    }

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
    }
}
