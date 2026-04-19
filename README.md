# Debuggable

`@Debuggable` is a library that leverages the Kotlin Compiler Plugin (KCP) to automatically track and visualize state changes in **Compose State** and **Coroutines Flow**, as well as **actions inside ViewModels/functions**.

The compiler takes care of the tedious work of instrumenting logs and preventing memory leaks (unsubscribing observers), dramatically improving the debugging experience during development.

---

## 🛠 User Guide

### 1. Installation

Available on Maven Central. Two things to wire up: the Gradle plugin (which applies the compiler plugin to your Kotlin compilations) and the runtime library (which the injected code calls into).

Make sure `mavenCentral()` is in both your plugin and dependency repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // only needed for Android
    }
}
```

Then apply the plugin and add the runtime dependency in your module's `build.gradle.kts`:

**Kotlin/JVM, Android, KMP (JVM target)**

```kotlin
plugins {
    kotlin("jvm") // or kotlin("android"), kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin") version "0.1.3"
}

dependencies {
    implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.3")
}
```

**Kotlin Multiplatform (common source set)**

```kotlin
plugins {
    kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin") version "0.1.3"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.3")
        }
    }
}
```

The runtime ships for `jvm`, `androidTarget`, `js`, `wasmJs`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `linuxX64`, and `mingwX64`.

On Android, logs go to Logcat with tag `"Debuggable"` by default (via `AndroidLogcatLogger`). Everywhere else, they go to stdout with a `[Debuggable]` prefix. See [§3 Replacing the Logger](#3-replacing-the-logger) to redirect.

### 2. Basic Usage
Simply annotate a class with `@Debuggable`, and the `State` and `Flow` within it will be tracked automatically.

```kotlin
@Debuggable
class SearchViewModel : ViewModel() {
    // Value changes are automatically logged, and observation is released when the ViewModel is cleared
    val searchQuery = MutableStateFlow("")
    val uiState = mutableStateOf(UiState())

    // Function calls (actions) are also automatically recorded along with their arguments
    fun onSearchClicked(query: String) { ... }
}
```

### 3. Replacing the Logger

By default, logs go to Android Logcat (tag `"Debuggable"`) on Android and stdout (`[Debuggable] …` prefix) everywhere else. You can route them to Timber, a file, a test collector, or anything else using any of three mechanisms (higher wins):

| Priority | Mechanism | Scope |
| :--- | :--- | :--- |
| 1 | `@Debuggable(logger = MyLogger::class)` | The annotated class / variable |
| 2 | Gradle DSL `debuggable { defaultLogger.set("FQN") }` | The entire module (compile-time) |
| 3 | `DefaultDebugLogger.current = DebugLogger { ... }` | The entire process (runtime) |
| 4 | Platform default (Logcat on Android, stdout elsewhere) | — |

All logger targets must be singleton `object` declarations that implement `DebugLogger`.

```kotlin
import me.tbsten.debuggable.runtime.logging.*

// (1) Per-class override
object AuthLogger : DebugLogger {
    override fun log(message: String) = Log.d("Auth", message)
}

@Debuggable(isSingleton = true, logger = AuthLogger::class)
object AuthStore { /* ... */ }

// (2) Module-wide (Gradle DSL) — see section 4.
// (3) Process-wide runtime swap — set once during app startup:
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DefaultDebugLogger.current = AndroidLogcatLogger    // built-in
    }
}
```

**Built-in loggers** shipped in `debuggable-runtime`:

| Logger | Source set | Description |
| :--- | :--- | :--- |
| `DebugLogger.Stdout` | commonMain | `println("[Debuggable] ...")` sink. Default on non-Android platforms. |
| `SilentLogger` | commonMain | No-op sink. Silences logs while keeping the plugin enabled. |
| `PrefixedLogger(prefix, delegate)` | commonMain | Prepends a prefix and forwards to another `DebugLogger`. |
| `InMemoryLogger()` | commonMain | Records every message into a list. Designed for unit tests — assert against `logger.messages`. |
| `CompositeLogger(vararg loggers)` | commonMain | Fans out every message to multiple sinks (e.g. keep stdout while also capturing in memory). |
| `FileLogger(file, append)` | jvm + androidMain | Appends each message as a line to a `java.io.File`. Thread-safe; creates parent dirs; flushes per write. |
| `AndroidLogcatLogger` / `AndroidLogcatLogger(tag)` | androidMain | `Log.d(tag, message)`. Default tag is `"Debuggable"`. Default on Android. |

### 4. Configuration

The Gradle plugin exposes per-feature toggles and a compile-time default logger so individual aspects can be disabled or redirected without runtime setup.

```kotlin
debuggable {
    enabled.set(true)          // master switch (default: true)
    observeFlow.set(true)      // wrap Flow/State initializers (default: true)
    logAction.set(true)        // log public method calls (default: true)
    defaultLogger.set("")      // FQN of a DebugLogger object to use as the module-wide
                               // default (empty = DefaultDebugLogger, which can still be
                               // replaced at runtime). Example:
                               // defaultLogger.set("com.example.myapp.MyDebugLogger")
}
```

When `enabled = false`, the plugin is a complete no-op — no IR transformations, no runtime dependency surfaces in the output binary. When `observeFlow` or `logAction` is individually disabled, only that transformation is skipped.

### 5. Internal Mechanism

A high-level view of what the plugin injects into your classes at compile time. You don't need to know any of this to use `@Debuggable`, but it helps when something behaves unexpectedly.

#### 5.1 Injecting Cleanup Strategies
It determines the type of the class and injects runtime unsubscription logic.
* **ViewModel / AutoCloseable:** Adds a private `DebugCleanupRegistry` backing field (initialized inline) and wraps the existing `close()` body in `try { ...original... } finally { registry.close() }`. For ViewModels this fires when `onCleared()` runs (since `ViewModel` implements `AutoCloseable` as of Lifecycle 2.7); for plain `AutoCloseable` it fires on explicit `close()`. If the class inherits `close()` without overriding it, a compile-time warning is emitted instead of silently leaking.
* **Local Variable:** Restructures the enclosing function so everything after the `@Debuggable` local is wrapped in `try { ... } finally { registry.close() }`, running cleanup on every function-exit path.

#### 5.2 Wrapping Properties
Initialization expressions for target `State` or `Flow` properties are wrapped with debugging functions provided by the Runtime library. The wrapper launches a background observation in the registry's coroutine scope and returns the underlying state/flow unchanged.

```kotlin
// Before transformation
val uiState = mutableStateOf(UiState())

// After IR transformation (conceptual)
val uiState = mutableStateOf(UiState())
    .debuggableState(name = "uiState", registry = $$debuggable_registry, logger = DefaultDebugLogger)
```

#### 5.3 Type-Based Intelligent Detection
The compiler checks the fully qualified class name (FQDN) of properties to determine whether the type is trackable.
* `androidx.compose.runtime.State` / `MutableState`
* `kotlinx.coroutines.flow.Flow` / `StateFlow` / `MutableStateFlow`

If `@FocusDebuggable` or similar is applied to a type other than these, a warning is emitted at compile time.

#### 5.4 Zero Overhead in Release Builds
When `enabled.set(false)` is configured on the Gradle plugin side (the default for Release builds), the KCP performs no IR transformations. No debugging code or Runtime library dependencies remain in the production binary, so there is zero performance impact.

---

### 6. Release Builds & Privacy

> **⚠️ Important:** With default settings, `@Debuggable` logs the current value of every tracked `State` / `Flow` and the full argument list of every annotated action — using each value's `toString()`. That can include tokens, passwords, email addresses, and other personal data. **Do not ship production builds with Debuggable enabled unless you understand this trade-off.**

Recommended configurations:

```kotlin
// Recommended for most apps — Debuggable only in debug-style builds.
debuggable {
    enabled.set(
        providers.gradleProperty("debuggable.enabled")
            .map { it.toBoolean() }
            .orElse(project.findProperty("buildType") != "release"),
    )
}
```

```kotlin
// Alternative — keep the plugin enabled but silence output in release.
// Useful if you want the cleanup side of the injection (AutoCloseable /
// try-finally registry.close()) without the logs.
import me.tbsten.debuggable.runtime.logging.SilentLogger

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.BUILD_TYPE == "release") {
            DefaultDebugLogger.current = SilentLogger
        }
    }
}
```

Per-property / per-class opt-outs:

- `@IgnoreDebuggable` on a property to exclude a single `State` / `Flow` from tracking.
- `@Debuggable(logger = MyLogger::class)` with `MyLogger` redacting sensitive values before forwarding to Logcat / stdout.
- `@FocusDebuggable` to flip a class into "only these properties" mode — everything else is ignored.

R8 / ProGuard: the runtime AAR ships consumer rules under `META-INF/proguard/debuggable-runtime.pro`, so Android consumers don't need any keep rules of their own. The injected calls survive minification automatically.

---

## 🔍 How It Works

Behaviors the plugin automatically manages for you. Reading this section helps you predict when your logs will appear and how to filter what gets tracked.

### 1. Memory Management and Cleanup
The observation lifecycle is automatically determined based on the nature of the class.

| Target | Condition | Cleanup Timing |
| :--- | :--- | :--- |
| **ViewModel** | Inherits from `ViewModel` | `onCleared()` (via the `AutoCloseable.close()` that `ViewModel` implements as of Lifecycle 2.7) |
| **AutoCloseable** | Implements `AutoCloseable` | When the `close()` method is executed |
| **Singleton** | `isSingleton = true` | None (persists until process termination) |
| **Local Variable** | Variable inside a function | When function execution ends (Scope Exit) |

```kotlin
// Temporary observation inside a function
fun performTask() {
    @Debuggable
    val tempState = mutableStateOf("Pending")
    // Automatically cleaned up when leaving the function
}

// When treating as a singleton
@Debuggable(isSingleton = true)
class GlobalSettings { ... }
```

### 2. Tracking Filters
You can focus on specific properties or exclude noise.

```kotlin
@Debuggable
class ComplexViewModel : ViewModel() {
    // When @Focus is present, "only" this one becomes the tracking target (Focus mode)
    @FocusDebuggable
    val targetState = mutableStateOf(0)

    // In normal mode, adding this excludes it from tracking
    @IgnoreDebuggable
    val noiseState = mutableStateOf("")
}
```

---

## 🧪 Try it Locally

The repository ships with two runnable samples under [`integration-test/`](integration-test/) that consume the plugin from `mavenLocal()`.

### 1. Publish the plugin locally

From the repo root:

```bash
./gradlew publishToMavenLocal
```

This installs `debuggable-runtime`, `debuggable-compiler`, and `debuggable-gradle` (version `0.1.3`) into `~/.m2/`.

### 2. Pick a sample

| Sample | Target | Lifecycle pattern | README |
|--------|--------|-------------------|--------|
| [`integration-test/cmp`](integration-test/cmp) | Compose Multiplatform Desktop (JVM) | `@Debuggable(isSingleton = true) object` | [cmp/README.md](integration-test/cmp/README.md) |
| [`integration-test/android`](integration-test/android) | Android app | `@Debuggable class : ViewModel(), AutoCloseable` | [android/README.md](integration-test/android/README.md) |

Each sample README explains how to run it, what to click, and where in the source the `@Debuggable` annotation is applied. See [`integration-test/README.md`](integration-test/README.md) for a side-by-side summary.

### 3. Run the full matrix locally (optional)

Both verification scripts support a parallel mode that mirrors CI's matrix on a single machine:

```bash
./scripts/smoke-test-all.sh              # parallel (default: min(nproc/2, 4))
./scripts/smoke-test-all.sh --serial     # one version at a time (easier to debug)
./scripts/smoke-test-all.sh --parallel 6 # override worker count
DEBUGGABLE_PARALLEL=6 ./scripts/test-all.sh
```

Each worker runs in an isolated rsync'd copy of the project (`.local/tmp/…`) so parallel Gradle invocations don't collide on `build/`. Per-version logs land in `.local/tmp/{smoke,test}-all-<version>.log`.

---

## 🧷 Supported Kotlin Versions

Every Kotlin 2.0+ stable patch through the latest beta is supported. Verified end-to-end
by `scripts/smoke-test-all.sh` (integration build of `integration-test/cmp` with each
target compiler) and `scripts/test-all.sh` (`:debuggable-compiler:test` under the
matching `kctfork`):

| Kotlin version | Status |
|:---|:---|
| 2.4.0-Beta1 | ✅ Verified |
| 2.3.21-RC2  | ✅ Verified |
| 2.3.20      | ✅ Verified (pinned build) |
| 2.3.10      | ✅ Verified |
| 2.3.0       | ✅ Verified |
| 2.2.21      | ✅ Verified |
| 2.2.20      | ✅ Verified |
| 2.2.10      | ✅ Verified |
| 2.2.0       | ✅ Verified |
| 2.1.21      | ✅ Verified |
| 2.1.20      | ✅ Verified |
| 2.1.10      | ✅ Verified |
| 2.1.0       | ✅ Verified |
| 2.0.21      | ✅ Verified |
| 2.0.20      | ✅ Verified |
| 2.0.10      | ✅ Verified |
| 2.0.0       | ✅ Verified |

### How multi-version works internally

The IR transformation logic lives in a metro-style per-Kotlin-version compat layer so a
single plugin artifact can dispatch to whichever implementation matches the consumer's
Kotlin compiler:

| Module | `minVersion` | Target compiler | Notes |
|:---|:---|:---|:---|
| `debuggable-compiler-compat-k2000` | 2.0.0  | 2.0.10 | Pre-`builders.kt` split; `createDiagnosticReporter` still returns `IrMessageLogger` |
| `debuggable-compiler-compat-k2020` | 2.0.20 | 2.0.21 | 2.1.20 未満 IR API (`putValueArgument` / `extensionReceiver=` / `valueParameters`) |
| `debuggable-compiler-compat-k21`   | 2.1.20 | 2.1.21 | New arg/receiver APIs, but `irCall` etc. still on `IrBuilderWithScope` |
| `debuggable-compiler-compat-k23`   | 2.2.0  | 2.3.20 | `irCall` on `IrBuilder`; new `arguments[param]=` API is the only one used |

At runtime, `IrInjectorLoader` (in `debuggable-compiler/compat/`) enumerates
`IrInjector.Factory` via `ServiceLoader`, skips any factories whose classes can't be
linked on the current runtime (e.g. `k23` fails on 2.0.x because `IrBuilder.irCall`
doesn't exist there), and picks the highest `minVersion` that is still `≤` the running
compiler. 2.4.0-Beta1 goes through a reflection-only helper for the `FirExtensionRegistrarAdapter`
/ `getAnnotation` signature drift, which is narrow enough to live inside `compat-k23`.

See `.github/workflows/ci.yml` for the 17-version CI matrix.
