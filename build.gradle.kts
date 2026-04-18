import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.buildconfig).apply(false)
}

val debuggableVersion = project.property("debuggable.version") as String
val debuggableGroup = "me.tbsten.debuggable"
val projectUrl = "https://github.com/TBSten/debuggable-compiler-plugin"

allprojects {
    group = debuggableGroup
    version = debuggableVersion
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            coordinates(debuggableGroup, project.name, debuggableVersion)
            pom {
                url = projectUrl
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "TBSten"
                        name = "tbsten"
                        url = "https://github.com/TBSten"
                    }
                }
                scm {
                    url = projectUrl
                    connection = "scm:git:https://github.com/TBSten/debuggable-compiler-plugin.git"
                    developerConnection = "scm:git:ssh://git@github.com/TBSten/debuggable-compiler-plugin.git"
                }
            }
            if (project.hasProperty("signing.keyId")) {
                signAllPublications()
            }
        }
    }
}
