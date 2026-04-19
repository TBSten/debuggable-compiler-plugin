# FIR vs IR phase diagnostics — design decision

> Status: decision recorded 2026-04. Revisit when Kotlin 2.5 ships (FIR
> diagnostic API is expected to stabilise further and the compat surface may
> shrink).

## Context

The plugin needs several compile-time diagnostics:

- `@Debuggable(logger = X::class)` — X must be an `object` and implement `DebugLogger`
- `@FocusDebuggable` on a non-`Flow`/`State` `val` with no setter → no-op warning
- `@FocusDebuggable` / `@IgnoreDebuggable` on a class that forgot `@Debuggable` → "stray annotation" warning
- `@FocusDebuggable` and `@IgnoreDebuggable` on the same element → error
- `@Debuggable(isSingleton = true)` on a class (not an `object`) → warning
- `@Debuggable` on a class that's neither singleton nor `AutoCloseable` → error
- Gradle DSL `defaultLogger.set("FQN")` — FQN must resolve to a loadable object → warning on failure

These could be implemented either in the **FIR checker phase** (runs during
frontend analysis, before IR generation) or the **IR transformer phase** (runs
during backend IR lowering, just before bytecode).

## Trade-offs

|   | FIR phase | IR phase (current) |
|---|---|---|
| Feedback timing | Frontend — fastest | Backend — still compile-time, slightly later |
| IDE integration | Rich (error/warning with squiggle and quick-fix) | Limited (surfaces via MessageCollector, not PSI) |
| API stability across Kotlin 2.0 → 2.4 | Drifts heavily; diagnostic factory registration changes across 2.1.20 / 2.2.0 / 2.3.20 | Stable (our compat-kXX layer already abstracts the IR builder drift) |
| Maintenance cost | 4 implementations (one per compat module) | 1 or shared implementation |
| Cross-plugin surface | Needs `FirAdditionalCheckersExtension` with version-specific registration | Inline in the existing visitor |

## Decision

**Keep these diagnostics in the IR phase for now. Defer FIR migration until the
cross-version FIR diagnostic API stabilises.**

All diagnostics listed above are implemented in each compat module's
`DebuggableClassTransformer.visitClass`:

- `transformDebuggableClass(...)` — checks that run on `@Debuggable` classes
  (logger validation, focus/ignore conflict, `isSingleton` mis-application).
- `warnStrayFocusIgnoreAnnotations(...)` — runs on every class to catch
  `@FocusDebuggable` / `@IgnoreDebuggable` on non-`@Debuggable` classes.
- `LoggerResolver` emits an error when the Gradle-supplied
  `defaultLogger` FQN doesn't resolve.

The trade-off we accept is that errors appear at **backend compilation time**,
one phase later than ideal, and the IDE shows them as build output rather than
inline squiggles. Every diagnostic still has a compile-time test in
`FocusIgnoreTests.kt` / `CompilationTests.kt` / `LoggerValidationTests.kt` /
`GradleDefaultLoggerTests.kt`.

## When to revisit

Migrate a diagnostic to FIR if **any** of the below become true:

1. A FIR-level API that's stable across Kotlin 2.0 → current ships in the
   compat layer (likely Kotlin 2.5+ once `FirSession`'s checker registration
   settles).
2. A user-impact issue surfaces that requires IDE integration — e.g. code
   completion wants to exclude `@Debuggable`-invalid classes, or a quick-fix
   for "add `isSingleton = true`" becomes valuable.
3. A diagnostic is too late at IR phase — e.g. it has to fire before other
   compiler plugins in the pipeline process the same code.

For now, the `DebuggableFirAdditionalCheckersExtension` exists as plumbing
(registered through `DebuggableFirExtensionRegistrar`) but contains no
checkers. That empty shell stays in place so a future FIR migration doesn't
need to re-wire extension registration — it only needs to populate
`declarationCheckers`.
