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
include(":debuggable-compiler")
include(":debuggable-compiler-compat")
include(":debuggable-compiler-compat-k23")
include(":debuggable-compiler-compat-k21")
include(":debuggable-compiler-compat-k20")
include(":debuggable-gradle")

