# Debuggable

`@Debuggable` is a library that leverages the Kotlin Compiler Plugin (KCP) to automatically track and visualize state changes in **Compose State** and **Coroutines Flow**, as well as **actions inside ViewModels/functions**.

The compiler takes care of the tedious work of instrumenting logs and preventing memory leaks (unsubscribing observers), dramatically improving the debugging experience during development.

---

## 🛠 User Guide

### 1. Basic Usage
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

### 2. Memory Management and Cleanup
The observation lifecycle is automatically determined based on the nature of the class.

| Target | Condition | Cleanup Timing |
| :--- | :--- | :--- |
| **ViewModel** | Inherits from `ViewModel` | `onCleared()` (uses `addCloseable` internally) |
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

### 3. Tracking Filters
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

## 🏗 Internal IR Transformation

This plugin rewrites the **Kotlin IR (Intermediate Representation)** at compile time to automatically build debugging infrastructure that does not appear in the source code.

### 1. Injecting Cleanup Strategies
It determines the type of the class and injects runtime unsubscription logic.
* **ViewModel:** Generates code in the `init` block that creates a `DebugCleanupRegistry` and registers it with `addCloseable`.
* **AutoCloseable:** Hooks into the `close()` method and inserts a loop at the end that executes all registered cleanup functions.
* **Local Variable:** Restructures the entire function to be wrapped in a `try-finally` block, executing cleanup inside `finally`.

### 2. Wrapping Properties
Initialization expressions for target `State` or `Flow` properties are wrapped with debugging functions provided by the Runtime library.

```kotlin
// Before transformation
val uiState = mutableStateOf(UiState())

// After IR transformation (conceptual)
val uiState = mutableStateOf(UiState()).also { 
    debuggableState(host = this, name = "uiState", state = it) 
}
```

### 3. Type-Based Intelligent Detection
The compiler checks the fully qualified class name (FQDN) of properties to determine whether the type is trackable.
* `androidx.compose.runtime.State` / `MutableState`
* `kotlinx.coroutines.flow.Flow` / `StateFlow` / `MutableStateFlow`

If `@FocusDebuggable` or similar is applied to a type other than these, a warning is emitted at compile time.

### 4. Zero Overhead in Release Builds
When `enabled.set(false)` is configured on the Gradle plugin side (the default for Release builds), the KCP performs no IR transformations. No debugging code or Runtime library dependencies remain in the production binary, so there is zero performance impact.
