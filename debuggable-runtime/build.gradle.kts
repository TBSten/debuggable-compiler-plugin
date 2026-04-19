import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Pin the runtime library to Kotlin 2.0 language/api so it can be consumed
    // by projects on Kotlin 2.0+. Without this, classes compiled with 2.3.20
    // produce metadata that older compilers refuse to load (see smoke-test
    // failures on 2.0.21 / 2.1.21 — FirIncompatibleClassExpressionChecker).
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }

    androidTarget {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()
    mingwX64()

    // Keep the default `nativeMain`/`appleMain` hierarchy wiring intact when
    // we add the custom `jvmAndAndroidMain` shared source set below. Without
    // this call, declaring *any* custom source set disables the default
    // template and native targets lose the shared `nativeMain` actuals (e.g.
    // `platformDefaultLogger`).
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // stdlib is compileOnly so it is NOT listed in the published Gradle
            // module metadata. Otherwise Gradle would force-upgrade consumer
            // projects to `kotlin-stdlib:2.3.20` (metadata [2,3,0]) — a drop
            // that blocks Kotlin 2.0 / 2.1 compilers. Consumers' own Kotlin
            // Gradle plugin will always add a matching stdlib for their target.
            // Version-explicit coordinate is required because
            // `kotlin.stdlib.default.dependency=false` also disables the
            // version auto-resolution of `kotlin("stdlib")`.
            compileOnly(libs.kotlin.stdlib)

            // Same reasoning as stdlib: consumers choose their own Compose
            // version. Transitive `compose.runtime` from our module would
            // upgrade them to 1.10.3 (metadata [2,1,0]).
            compileOnly(compose.runtime)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // Tests run in-process, so we need stdlib and compose.runtime on
            // the runtime classpath even though they are compileOnly for main.
            implementation(libs.kotlin.stdlib)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(compose.runtime)
        }

        androidMain.dependencies {
            // Android needs stdlib on its actual compile classpath — the
            // commonMain `compileOnly(stdlib)` does not propagate to Android's
            // releaseCompileClasspath variant. Use `implementation` on this
            // source set only; the common variant publication still keeps
            // stdlib as compileOnly so the top-level `.module` requirement
            // stays loose enough for Kotlin 2.0 / 2.1 consumers.
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

        // Shared Java-IO source set for APIs that rely on `java.io.File`
        // (currently `FileLogger`). Both JVM and Android variants get it.
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        androidMain.get().dependsOn(jvmAndAndroidMain)

    }
}

android {
    namespace = "me.tbsten.debuggable.runtime"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
// Each module only needs to set its own name and description.
mavenPublishing {
    pom {
        name = "Debuggable Runtime"
        description = "Runtime library consumed by @Debuggable-annotated code (KMP)"
    }
}

// Post-process the published Gradle Module Metadata to strip `kotlin-stdlib`
// from outgoing dependency lists.
//
// Why: KMP requires `androidMain.dependencies { implementation(libs.kotlin.stdlib) }`
// for the Android target's compile classpath (commonMain's `compileOnly(stdlib)`
// does not propagate to Android's release variant; `compileOnly` on `androidMain`
// itself is rejected by KMP's dep-checker as of 2.x). That declaration leaks
// `kotlin-stdlib:<pinned>` into every variant's `.module` — which forces Gradle
// to upgrade stdlib on consumer projects to our pinned compiler's version and
// breaks any Kotlin 2.0.x / 2.1.x consumer with "metadata version 2.3.0,
// expected 2.0.0".
//
// Gradle lacks a clean programmatic way to remove a dependency from an already-
// published variant before the `.module` is written (AdhocComponentWithVariants'
// withVariantsFromConfiguration only supports filtering, not dep removal, and
// KMP uses its own SoftwareComponent). The pragmatic fix is to rewrite the JSON
// after generation, before it's uploaded. `generateMetadataFileForXxxPublication`
// emits the file; everything downstream (signing, publishing) picks it up as-is.
tasks.withType<GenerateModuleMetadata>().configureEach {
    doLast {
        val file = outputFile.get().asFile
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(file) as MutableMap<String, Any?>
        val variants = root["variants"] as? MutableList<MutableMap<String, Any?>> ?: return@doLast
        var changed = false
        variants.forEach { variant ->
            @Suppress("UNCHECKED_CAST")
            val deps = variant["dependencies"] as? MutableList<MutableMap<String, Any?>> ?: return@forEach
            val before = deps.size
            deps.removeAll { dep ->
                dep["group"] == "org.jetbrains.kotlin" && dep["module"] == "kotlin-stdlib"
            }
            if (deps.size != before) changed = true
            if (deps.isEmpty()) variant.remove("dependencies")
        }
        if (changed) {
            file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)))
            logger.lifecycle("[debuggable-runtime] stripped kotlin-stdlib from ${file.name}")
        }
    }
}

// Mirror the `.module` strip into the POM. Consumers that prefer POM over
// Gradle Module Metadata (older Gradle, explicit opt-out, non-Gradle Maven
// clients) would otherwise still see `kotlin-stdlib:<pinned>` with `runtime`
// scope in `debuggable-runtime-android-<v>.pom` — the only POM that leaks,
// because KMP's android target requires `implementation(stdlib)` on
// `androidMain`. `pom.withXml` runs mid-generation and is re-added to by AGP
// / KMP *after* our lambda, so it's unreliable as a final scrub. Post-process
// the generated file instead.
tasks.withType<org.gradle.api.publish.maven.tasks.GenerateMavenPom>().configureEach {
    doLast {
        val file = destination
        if (!file.exists()) return@doLast
        val original = file.readText()
        val stripped = original.replace(
            Regex(
                "\\s*<dependency>\\s*<groupId>org\\.jetbrains\\.kotlin</groupId>" +
                    "\\s*<artifactId>kotlin-stdlib</artifactId>[\\s\\S]*?</dependency>",
            ),
            "",
        )
        if (stripped != original) {
            file.writeText(stripped)
        }
    }
}
