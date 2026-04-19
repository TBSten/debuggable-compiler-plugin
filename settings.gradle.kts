rootProject.name = "Debuggable-Compiler-Plugin"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.20"
    }
    repositories {
        google {
            content {
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content { 
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}
include(":debuggable-runtime")
include(":debuggable-ui")
include(":debuggable-compiler")
// Per-Kotlin-version IR impl modules, nested under `:debuggable-compiler` to
// reflect their "part of the compiler plugin" status. Maven artifact IDs still
// derive from `project.path` dash-joined (see root build.gradle.kts), so
// consumers continue to see `debuggable-compiler-compat-kXX` coordinates.
include(":debuggable-compiler:compat")
include(":debuggable-compiler:compat:k23")
include(":debuggable-compiler:compat:k21")
include(":debuggable-compiler:compat:k2020")
include(":debuggable-compiler:compat:k2000")
include(":debuggable-gradle")

