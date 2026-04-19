import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.shadow)
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

    // Abstract IR injection API + ServiceLoader discovery, plus the four
    // per-Kotlin-version IR implementations. All five are bundled DIRECTLY
    // into `debuggable-compiler.jar` via the `jar` task below (see comment
    // there), so `compileOnly` — the classes are on the compile classpath
    // for this module's own sources, but NOT declared as published deps
    // (POM / Gradle Module Metadata). A self-contained JAR lets KMP non-JVM
    // targets (native / wasmJs / js) load the plugin: their
    // `kotlinCompilerPluginClasspath*` configurations filter transitive
    // JVM-only deps (`org.gradle.jvm.environment = standard-jvm`) and would
    // otherwise fail at IR generation with
    // `ClassNotFoundException: …compat.MessageCollectorHolder` or
    // `No IrInjector.Factory found on classpath`.
    compileOnly(project(":debuggable-compiler:compat"))
    compileOnly(project(":debuggable-compiler:compat:k23"))
    compileOnly(project(":debuggable-compiler:compat:k21"))
    compileOnly(project(":debuggable-compiler:compat:k2020"))
    compileOnly(project(":debuggable-compiler:compat:k2000"))

    // Test classpath still needs the compat impls at runtime — CompilerTestHelper
    // loads DebuggableCompilerPluginRegistrar in-process and ServiceLoader-discovers
    // the factories from the test JVM classpath. Published consumers get all of
    // these from the shadowJar bundle.
    testRuntimeOnly(project(":debuggable-compiler:compat"))
    testRuntimeOnly(project(":debuggable-compiler:compat:k23"))
    testRuntimeOnly(project(":debuggable-compiler:compat:k21"))
    testRuntimeOnly(project(":debuggable-compiler:compat:k2020"))
    testRuntimeOnly(project(":debuggable-compiler:compat:k2000"))

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

// Bundle the shared `debuggable-compiler-compat` classes into this JAR. See
// the `compileOnly(project(":...:compat"))` comment above for why — the short
// version: KMP non-JVM targets filter out the separate compat artifact, so we
// make the compiler plugin JAR self-contained.
// Bundle the shared compat module + all four per-Kotlin-version implementations
// (+ their META-INF/services ServiceLoader registrations) directly into the
// published JAR, making it a self-contained compiler plugin that works on
// every KMP target's compiler-plugin-classpath — not just JVM. A custom
// `bundled` configuration carries them so shadowJar can pick them up without
// leaking the deps into the published POM / Module Metadata (declared
// `compileOnly` above).
val bundled by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    bundled(project(":debuggable-compiler:compat"))
    bundled(project(":debuggable-compiler:compat:k23"))
    bundled(project(":debuggable-compiler:compat:k21"))
    bundled(project(":debuggable-compiler:compat:k2020"))
    bundled(project(":debuggable-compiler:compat:k2000"))
}

tasks.shadowJar {
    configurations = listOf(bundled)       // only include compat, not stdlib / kotlinc
    mergeServiceFiles()                    // merge ServiceLoader registrations
    dependsOn(tasks.jar)
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/MANIFEST.MF")
}

// Replace the plain `jar` with the shadow jar as the single primary artifact.
// Approach: shadowJar writes directly to the `archiveClassifier = ""` slot
// (same place the plain `jar` would land) and we disable the plain jar so
// there's no duplicate artifact for the `java` component metadata generator
// to trip over. Gradle Module Metadata is also disabled for this module —
// the Gradle plugin marker + its POM is what consumers actually resolve via
// `id("me.tbsten.debuggablecompilerplugin")`, and skipping .module here avoids
// the "artifact from the 'java' component has been removed" error that comes
// with swapping a component's main artifact.
tasks.shadowJar {
    archiveClassifier.set("")
}
tasks.jar {
    archiveClassifier.set("thin")
    // Without the `thin` classifier the plain jar would clash with shadow jar's
    // output path. Keeping the task enabled (Gradle expects artifacts in the
    // component even if we publish via shadowJar) but stashing it under a
    // non-published classifier.
}
tasks.named("assemble") { dependsOn(tasks.shadowJar) }

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
