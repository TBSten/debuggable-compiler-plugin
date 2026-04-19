rootProject.name = "debuggable-kmp-smoke"

pluginManagement {
    val kotlinVersion: String = (settings.providers.gradleProperty("integration.kotlin").orNull
        ?: "2.3.20")

    val debuggableVersion: String = (settings.providers.gradleProperty("integration.debuggable").orNull
        ?: "0.1.4")

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
