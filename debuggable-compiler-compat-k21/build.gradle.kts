plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    pom {
        name = "Debuggable Compiler Plugin — Kotlin 2.1.20+ impl"
        description = "IR transformation implementation for Kotlin 2.1.20 through 2.1.21"
    }
}

val debuggablePluginId = project.property("debuggable.pluginId") as String

buildConfig {
    packageName("me.tbsten.debuggable.compiler.compat.k21")
    buildConfigField("String", "PLUGIN_ID", "\"$debuggablePluginId\"")
}

dependencies {
    api(project(":debuggable-compiler-compat"))
    implementation(libs.kotlin.stdlib)
    // Compiled against Kotlin 2.1.21's compiler API so bytecode only references symbols
    // that existed in 2.1.x. ServiceLoader picks this impl when the running compiler is
    // 2.1.20 — 2.1.21 (where `pluginContext.messageCollector` exists but `irCall` is still
    // on `IrBuilderWithScope`).
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
