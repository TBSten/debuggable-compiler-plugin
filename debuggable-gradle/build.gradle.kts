plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        create("debuggable") {
            id = "me.tbsten.debuggable"
            implementationClass = "me.tbsten.debuggable.gradle.DebuggableGradlePlugin"
        }
    }
}
