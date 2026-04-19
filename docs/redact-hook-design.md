# Redact hook & value-logging opt-in (design memo)

> Status: **design draft** — not yet implemented. Tracks the direction agreed in
> task-501 and task-505. Create a chapter ticket before starting work.

## Motivation

Today `debuggableState` / `debuggableFlow` call `logger.log("$name: $value")`
with the value formatted by its `toString()`. For ViewModels backing auth
flows, payment screens, or anything with free-form text, that leaks PII into
Logcat / stdout by default.

We already expose three opt-outs (`@IgnoreDebuggable`, `SilentLogger`, a custom
`logger = ...`), but each is all-or-nothing and requires the developer to
anticipate the risk up front. A purpose-built redact hook lets privacy-sensitive
defaults be written once and apply everywhere.

## Proposed API (runtime)

Add a second logging method to `DebugLogger` so the injected call sites can
hand the logger the structured pieces instead of a pre-formatted string:

```kotlin
interface DebugLogger {
    /** Existing API — unchanged. Default delegates to [logEvent]. */
    fun log(message: String) = logEvent(DebugEvent.Raw(message))

    /** New entry point used by injected code from 0.2 onward. */
    fun logEvent(event: DebugEvent) {
        // Default impl preserves today's behavior exactly.
        log(event.render())
    }
}

sealed interface DebugEvent {
    /** State/Flow value change.  `value` may be anything, including secrets. */
    data class ValueChanged(val host: String, val name: String, val value: Any?) : DebugEvent
    /** Public-method invocation recorded by `logAction`. */
    data class Action(val host: String, val method: String, val args: List<Any?>) : DebugEvent
    /** Anything already pre-formatted — e.g. custom user messages. */
    data class Raw(val message: String) : DebugEvent

    fun render(): String = when (this) {
        is ValueChanged -> "$host.$name: $value"
        is Action       -> "$host.$method(${args.joinToString()})"
        is Raw          -> message
    }
}
```

A redacting logger becomes a delegate:

```kotlin
object RedactingLogger : DebugLogger {
    private val delegate = PlatformDefaultLogger

    override fun logEvent(event: DebugEvent) {
        val safe = when (event) {
            is DebugEvent.ValueChanged -> event.copy(value = mask(event.value))
            is DebugEvent.Action       -> event.copy(args = event.args.map(::mask))
            is DebugEvent.Raw          -> event
        }
        delegate.logEvent(safe)
    }

    private fun mask(value: Any?): Any? = when (value) {
        is String -> if (looksSensitive(value)) "***" else value
        else      -> value
    }
}
```

## Proposed API (annotation)

Add `@RedactDebuggable` at the property / parameter level so developers can
mark known-sensitive fields without writing a custom logger:

```kotlin
@Debuggable
class AuthViewModel : ViewModel() {
    @RedactDebuggable                                  // masked to "***"
    val passwordField = mutableStateOf("")
    val emailField = mutableStateOf("")                // normal logging

    fun login(user: String, @RedactDebuggable pw: String) { ... }
}
```

The compiler injector would either:
- pass a sentinel `RedactedValue` into `DebugEvent.ValueChanged.value`, or
- short-circuit the log call entirely for that property, emitting
  `"$name: <redacted>"`.

Emitting the sentinel is the more flexible option: custom loggers still see
that a change happened (useful for diffing / counting) without ever touching
the raw value.

## Opt-in value logging (task-501)

If we want to change the **default** from "log the value" to "log that a change
happened", that's a Gradle DSL toggle:

```kotlin
debuggable {
    logStateValues.set(false)   // default: true for compat with 0.1.x
    logActionArgs.set(false)
}
```

When `logStateValues = false`, `DebugEvent.ValueChanged.value` is replaced by
the sentinel before it reaches the logger. This is a conservative default we
can flip in a future major version.

## Migration

- 0.1.x: `DebugLogger.log(String)` is the only entry point. Behavior unchanged.
- 0.2.x: add `DebugEvent`, `logEvent`. Existing `log` becomes a default method
  delegating via `render()`. Custom loggers continue to work.
- 0.3.x (optional): flip the default of `logStateValues` to `false`.

## Test matrix for implementation

- `@RedactDebuggable val password = mutableStateOf("secret")` — logger receives
  sentinel / masked value, never `"secret"`.
- Nested Redact on a `Flow<User>` that emits a `data class` — ensure the whole
  payload is replaced, not just toString'd.
- Custom logger that only implements the old `log(String)` method — still
  receives pre-formatted output from the default `logEvent.render()`.
- `logStateValues = false` in Gradle DSL: injector emits the sentinel even for
  non-`@RedactDebuggable` properties.
- R8 keep rules: `DebugEvent` hierarchy must survive minification (update
  `debuggable-runtime.pro`).

## Non-goals

- Encryption / hashing of values on the logging path. Users who need that should
  write a custom logger.
- Structured log sinks (JSON, OpenTelemetry). Also a custom-logger concern.
- Per-call redaction of Compose state reads — only property-level opt-in.
