plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.maven.publish)
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
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.compiler.embeddable)
}
