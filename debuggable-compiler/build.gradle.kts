plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

val debuggablePluginId = project.property("debuggable.pluginId") as String

buildConfig {
    packageName("me.tbsten.debuggable.compiler")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
}

// Coordinates, license, developer, SCM, etc. come from the root build.gradle.kts.
mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin"
        description = "Kotlin compiler plugin that auto-instruments @Debuggable classes"
    }
}

dependencies {
    // stdlib is declared explicitly because `kotlin.stdlib.default.dependency=false`
    // is set project-wide (see gradle.properties for the reason).
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.compiler.embeddable)

    // Abstract IR injection API + ServiceLoader discovery. Per-version
    // implementations (`debuggable-compiler-compat-kXX`) are brought in as
    // `runtimeOnly` so their bytecode stays out of compile-time resolution.
    implementation(project(":debuggable-compiler-compat"))
    runtimeOnly(project(":debuggable-compiler-compat-k23"))
    runtimeOnly(project(":debuggable-compiler-compat-k21"))
    runtimeOnly(project(":debuggable-compiler-compat-k20"))

    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kctfork.core)
    testImplementation(kotlin("test"))
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
