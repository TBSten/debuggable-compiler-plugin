import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // The main plugin JAR must load on every supported Kotlin version
    // (2.0.0 – 2.4.0-Beta1). Pin to the oldest metadata level so older compilers
    // accept it.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
        // Target Java 17 bytecode so Gradle daemons on JDK 17 (Gradle 8's minimum)
        // can load this compiler plugin JAR. Publishing from CI's JDK 21 without
        // this would emit class-file major 65, rejected by JDK 17.
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val debuggablePluginId = project.property("debuggable.pluginId") as String

buildConfig {
    packageName("me.tbsten.debuggable.compiler")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
}

// ─────────────────────────────────────────────────────────────────────────────
// Parameterised test toolchain
//
// `-Ptest.kotlin=X.Y.Z` swaps the test-time `kotlin-compiler-embeddable` (and the
// matching `kctfork` release, which bundles the compiler). When unspecified, we
// fall back to the project's pinned Kotlin version.
//
// `kctfork` is tied 1:1 to a Kotlin compiler version. Keep this mapping in sync
// with https://github.com/ZacSweers/kotlin-compile-testing/releases. Versions
// without a matching kctfork (e.g. 2.3.21-RC2, 2.4.0-Beta1) must skip `:test`
// — the caller's `scripts/test-all.sh` handles that.
// ─────────────────────────────────────────────────────────────────────────────
val testKotlinVersion: String = (findProperty("test.kotlin") as String?)
    ?: libs.versions.kotlin.get()

val kctforkForKotlin: String = when {
    testKotlinVersion.startsWith("2.4") -> "0.12.1" // no dedicated release; try latest 2.3.x line
    testKotlinVersion.startsWith("2.3") -> "0.12.1" // 2.3.0 / 2.3.10 / 2.3.20 all compatible
    testKotlinVersion.startsWith("2.2.20") || testKotlinVersion.startsWith("2.2.21") -> "0.11.0"
    testKotlinVersion.startsWith("2.2.10") -> "0.9.0"
    testKotlinVersion.startsWith("2.2") -> "0.8.0" // 2.2.0
    testKotlinVersion.startsWith("2.1") -> "0.7.1" // 2.1.0 – 2.1.21 (0.7.1 ships 2.1.10 but API-compatible)
    testKotlinVersion.startsWith("2.0.21") -> "0.6.0"
    testKotlinVersion.startsWith("2.0") -> "0.5.0" // 2.0.0 / 2.0.10 / 2.0.20
    else -> libs.versions.kctfork.get()
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin"
        description = "Kotlin compiler plugin that auto-instruments @Debuggable classes"
    }
}

dependencies {
    // stdlib is `compileOnly`: kotlinc already has stdlib on its classpath when it
    // loads our plugin, and `implementation(kotlin-stdlib)` would leak a fixed
    // version onto the test classpath (making `-Ptest.kotlin=X` runs fail when X
    // < the pinned version's metadata level).
    // Note: `kotlin.stdlib.default.dependency=false` in gradle.properties prevents
    // the Kotlin plugin from auto-adding stdlib, so we must declare it ourselves.
    compileOnly(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.stdlib) { version { strictly(testKotlinVersion) } }
    compileOnly(libs.kotlin.compiler.embeddable)

    // Abstract IR injection API + ServiceLoader discovery. Per-version
    // implementations (`debuggable-compiler-compat-kXX`) are brought in as
    // `runtimeOnly` so their bytecode stays out of compile-time resolution.
    implementation(project(":debuggable-compiler:compat"))
    runtimeOnly(project(":debuggable-compiler:compat:k23"))
    runtimeOnly(project(":debuggable-compiler:compat:k21"))
    runtimeOnly(project(":debuggable-compiler:compat:k2020"))
    runtimeOnly(project(":debuggable-compiler:compat:k2000"))

    // Version-matched test toolchain — see `testKotlinVersion` / `kctforkForKotlin`
    // at the top of this file.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$testKotlinVersion")
    testImplementation("dev.zacsweers.kctfork:core:$kctforkForKotlin")
    // `kotlin("test")` resolves to the project's Kotlin plugin version; pin it so its
    // metadata version matches what the test compiler accepts.
    testImplementation("org.jetbrains.kotlin:kotlin-test:$testKotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$testKotlinVersion")
    testImplementation(project(":debuggable-runtime"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
