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
// Matches the Sonatype Central Portal verified namespace. All 8 published artifacts
// (`debuggable-runtime`, `debuggable-compiler`, `debuggable-compiler-compat*`,
// `debuggable-gradle`) and the Gradle plugin marker live under this groupId.
val debuggableGroup = "me.tbsten.debuggablecompilerplugin"
val projectUrl = "https://github.com/TBSten/debuggable-compiler-plugin"

allprojects {
    group = debuggableGroup
    version = debuggableVersion
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            // Artifact ID derives from the full Gradle project path with ":" joined by
            // "-". This keeps Maven coordinates flat (`debuggable-compiler-compat-k23`)
            // even though the project tree is nested (`:debuggable-compiler:compat:k23`),
            // so consumers on 0.1.0 see no change after the module restructure.
            val artifactId = project.path.removePrefix(":").replace(":", "-")
            coordinates(debuggableGroup, artifactId, debuggableVersion)
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
            // Enable GPG signing if either path has credentials: vanniktech's in-memory
            // flow (`ORG_GRADLE_PROJECT_signingInMemoryKey` → `signingInMemoryKey` property,
            // used by the Publish workflow) or the classic signing plugin properties
            // (`signing.keyId`, still handy for local dev off a keychain).
            if (project.hasProperty("signingInMemoryKey") || project.hasProperty("signing.keyId")) {
                signAllPublications()
            }
        }
    }
}
