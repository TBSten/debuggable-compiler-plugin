# Integration Test Samples

Local sample projects that consume the `me.tbsten.debuggablecompilerplugin` plugin from `mavenLocal()`.

## Prerequisites

From the repo root, publish the plugin and runtime to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

This publishes:
- `me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.7` (KMP targets)
- `me.tbsten.debuggablecompilerplugin:debuggable-compiler:0.1.7`
- `me.tbsten.debuggablecompilerplugin:debuggable-gradle:0.1.7`

Re-run after any change to the plugin or runtime.

## Multi-Kotlin-version testing

Both samples accept a `-Pintegration.kotlin=<version>` property to swap the Kotlin toolchain.
The Compose Multiplatform plugin version is mapped automatically in
`cmp/settings.gradle.kts` based on the chosen Kotlin version; override with
`-Pintegration.compose=<version>` or `-Pintegration.agp=<version>` (Android) if needed.

```bash
# From repo root — run the full smoke matrix for all supported Kotlin versions
./scripts/smoke-test-all.sh

# Or a single version
./scripts/smoke-test.sh 2.2.20

# Manually from inside a sample
cd integration-test/cmp
./gradlew build -Pintegration.kotlin=2.4.0-Beta1
```

`scripts/smoke-test.sh` publishes the plugin to `mavenLocal()`, builds the CMP sample with
the target Kotlin, and uses `javap` to assert the injected symbols (`debuggableFlow`,
`logAction`) are present in the output bytecode.

## Compose Multiplatform Desktop (`cmp/`)

Launch the desktop app:

```bash
cd integration-test/cmp
./gradlew run
```

A window opens with a counter. Click `+1`, `-1`, or `Next label` — each state change prints a `[Debuggable]` log line to the terminal. Method calls are also logged.

### Main code

| Path | Purpose |
|------|---------|
| [`cmp/build.gradle.kts`](cmp/build.gradle.kts) | Applies `id("me.tbsten.debuggablecompilerplugin")` and adds `debuggable-runtime` |
| [`cmp/src/jvmMain/kotlin/example/Main.kt`](cmp/src/jvmMain/kotlin/example/Main.kt) | `@Debuggable(isSingleton = true) object CounterStore` + Compose UI |

### Key excerpt — `Main.kt`

```kotlin
private val GREETINGS = listOf("Hello", "Bonjour", "こんにちは", "Hola", "Hallo", "안녕하세요")

@Debuggable(isSingleton = true)
object CounterStore {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()    // ← auto-tracked

    private val _label = MutableStateFlow(GREETINGS.first())
    val label: StateFlow<String> = _label.asStateFlow() // ← auto-tracked

    private var labelIndex = 0

    fun increment() { _count.update { it + 1 } }        // ← logs on call
    fun decrement() { _count.update { it - 1 } }        // ← logs on call
    fun nextLabel() {                                   // ← logs on call
        labelIndex = (labelIndex + 1) % GREETINGS.size
        _label.value = GREETINGS[labelIndex]
    }
}
```

### Key excerpt — `build.gradle.kts`

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
    id("me.tbsten.debuggablecompilerplugin") version "0.1.7"   // ← apply the plugin
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.7")
                // ...
            }
        }
    }
}
```

## Android (`android/`)

Build the APK:

```bash
cd integration-test/android
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install on a device/emulator:

```bash
./gradlew :app:installDebug
adb logcat | grep Debuggable
```

### Main code

| Path | Purpose |
|------|---------|
| [`android/app/build.gradle.kts`](android/app/build.gradle.kts) | Applies `id("me.tbsten.debuggablecompilerplugin")` and adds `debuggable-runtime` |
| [`android/app/src/main/kotlin/example/debuggable/android/MainActivity.kt`](android/app/src/main/kotlin/example/debuggable/android/MainActivity.kt) | `@Debuggable class CounterViewModel : ViewModel(), AutoCloseable` + Compose UI |
| [`android/app/src/main/AndroidManifest.xml`](android/app/src/main/AndroidManifest.xml) | Launcher activity declaration |

### Key excerpt — `MainActivity.kt`

```kotlin
private val STATUSES = listOf("Idle", "Loading", "Running", "Success", "Error", "Done")

@Debuggable
class CounterViewModel : ViewModel(), AutoCloseable {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()    // ← auto-tracked

    private val _label = MutableStateFlow(STATUSES.first())
    val label: StateFlow<String> = _label.asStateFlow() // ← auto-tracked

    private var labelIndex = 0

    fun increment() { _count.update { it + 1 } }        // ← logs on call
    fun nextLabel() {                                   // ← logs on call
        labelIndex = (labelIndex + 1) % STATUSES.size
        _label.value = STATUSES[labelIndex]
    }

    override fun close() {
        // Plugin wraps this in try-finally so the registry is released
        // when the ViewModel is cleared.
    }
}
```

### Key excerpt — `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("me.tbsten.debuggablecompilerplugin")   // ← apply the plugin
}

dependencies {
    implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.7")
    // ...
}
```

## Verifying the plugin ran

If IR injection worked, you'll see these references in the generated bytecode:

```bash
# Android
javap -p -c android/app/build/tmp/kotlin-classes/debug/example/debuggable/android/CounterViewModel.class \
  | grep -E "debuggable|logAction"

# CMP
javap -p -c cmp/build/classes/kotlin/jvm/main/example/CounterStore.class \
  | grep -E "debuggable|logAction"
```

You should see `debuggableFlow`, `logAction`, and a `$$debuggable_registry` field (only for non-singleton / AutoCloseable classes).
