rootProject.name = "debuggable-android-sample"

pluginManagement {
    val kotlinVersion: String = (settings.providers.gradleProperty("integration.kotlin").orNull
        ?: "2.3.20")
    val agpVersion: String = (settings.providers.gradleProperty("integration.agp").orNull
        ?: "8.12.3")
    // Canonical default is `debuggable.version` in this sample's
    // `gradle.properties` (kept in sync with the repo root via
    // `/bump-library-version`). `-Pintegration.debuggable=X.Y.Z` still wins
    // when invoked explicitly (e.g. from scripts/smoke-test.sh).
    val debuggableVersion: String = settings.providers.gradleProperty("integration.debuggable").orNull
        ?: settings.providers.gradleProperty("debuggable.version").orNull
        ?: error("debuggable.version missing — set it in gradle.properties or pass -Pintegration.debuggable=…")

    plugins {
        id("com.android.application") version agpVersion
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("me.tbsten.debuggablecompilerplugin") version debuggableVersion
    }

    repositories {
        mavenLocal()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
    }
}

dependencyResolutionManagement {
    // `gradle/libs.versions.toml` next to this settings file is auto-discovered
    // by Gradle as the `libs` catalog — no explicit `versionCatalogs { ... }`.

    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-eap")
    }
}

include(":app")
