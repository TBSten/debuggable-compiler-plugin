rootProject.name = "debuggable-android-sample"

pluginManagement {
    val kotlinVersion: String = (settings.providers.gradleProperty("integration.kotlin").orNull
        ?: "2.3.20")
    val agpVersion: String = (settings.providers.gradleProperty("integration.agp").orNull
        ?: "8.12.3")
    val debuggableVersion: String = (settings.providers.gradleProperty("integration.debuggable").orNull
        ?: "0.1.0")

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
