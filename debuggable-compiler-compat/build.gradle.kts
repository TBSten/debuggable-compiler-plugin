import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.maven.publish)
}

kotlin {
    // This module must load cleanly on every supported Kotlin version
    // (2.0.0 – 2.4.0-Beta1). Pin to 2.0.0 so its classfile metadata is
    // the oldest readable level.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin Compat API"
        description = "Version-stable interface + ServiceLoader discovery for the Debuggable compiler plugin's per-Kotlin-version implementations"
    }
}

dependencies {
    // Version-stable portion of the compiler API — the interface + ServiceLoader discovery.
    // Per-version implementations live in sibling modules that target their own
    // `kotlin-compiler-embeddable` version.
    //
    // `compileOnly` stdlib (not `implementation`): consumer's kotlinc already supplies
    // stdlib at runtime. Propagating `kotlin-stdlib:<pinned>` from this module's POM
    // would pin consumers / tests to our compile version's metadata level.
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.compiler.embeddable)
}
