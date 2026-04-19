# Supported Kotlin versions

> The authoritative source for the supported version list is
> [`scripts/supported-kotlin-versions.txt`](../scripts/supported-kotlin-versions.txt).
> Shell scripts and CI workflows read from that file. This document explains
> **how the support works internally** and **how to add a new version**.

## Current matrix

Every Kotlin 2.0+ stable patch through the latest beta is covered:

```
2.4.0-Beta1   (beta — pinned build)
2.3.21-RC2    (release candidate)
2.3.20        (pinned stable — this project is built against this version)
2.3.10
2.3.0
2.2.21
2.2.20
2.2.10
2.2.0
2.1.21
2.1.20
2.1.10
2.1.0
2.0.21
2.0.20
2.0.10
2.0.0
```

See the README's "Supported Kotlin Versions" section for the per-version status
table.

## Compat layer mapping

A single published plugin JAR has to link cleanly on every supported Kotlin
compiler, even though the IR builder APIs shift across 2.0.x / 2.0.20 / 2.1.20 /
2.2+. We handle this with a metro-style dispatcher:

| Module | `minVersion` | Target compiler | Key IR API assumption |
|:---|:---|:---|:---|
| `debuggable-compiler-compat-k2000` | 2.0.0  | 2.0.10 | Pre-`builders.kt` split; `createDiagnosticReporter` returns `IrMessageLogger` |
| `debuggable-compiler-compat-k2020` | 2.0.20 | 2.0.21 | `putValueArgument` / `extensionReceiver=` / `valueParameters` |
| `debuggable-compiler-compat-k21`   | 2.1.20 | 2.1.21 | New arg/receiver APIs; `irCall` still on `IrBuilderWithScope` |
| `debuggable-compiler-compat-k23`   | 2.2.0  | 2.3.20 (+ 2.3.21-RC2 / 2.4.0-Beta1 via reflection) | `irCall` on `IrBuilder`; new `arguments[param] =` API |

The main plugin module (`debuggable-compiler`) itself uses only version-agnostic
API. At runtime, `IrInjectorLoader`:

1. Enumerates `IrInjector.Factory` entries via `ServiceLoader`.
2. Drops any factory whose class can't be linked on the current runtime
   (`NoClassDefFoundError` / `ServiceConfigurationError`).
3. Picks the factory with the highest `minVersion` that is still `<=` the running
   compiler's version (read from `META-INF/compiler.version`).
4. Emits an `INFO` diagnostic naming the chosen factory — visible in CI logs and
   `--info` builds for debugging "why didn't the plugin run" reports.

For 2.3.21-RC2 / 2.4.0-Beta1, `compat-k23` additionally uses reflection to bridge
a few `FirExtensionRegistrarAdapter` / `getAnnotation` signature drifts. That
surface is intentionally narrow; if a future stable Kotlin widens it, a new
`compat-k24` module should take over.

## Support policy

1. **Every stable patch from 2.0.0 through the latest released minor is supported
   at all times.** Adding a new patch is a chore, not a decision.
2. **The most recent `Beta` / `RC` of an in-development Kotlin is kept in the
   matrix** so drift is caught early, not at the next-minor release.
3. **Pre-release channels (EAP, dev) are not promised** but PRs wiring them up
   are welcome.
4. **No version is removed silently.** Dropping an older Kotlin (e.g. the whole
   2.0.x line) requires a minor version bump of this plugin and a CHANGELOG
   entry calling it out.
5. **The pinned build Kotlin** (`gradle/libs.versions.toml: kotlin`) is also in
   the matrix and is the version the project itself compiles with.

## Adding a new Kotlin version

Happy path (a new patch in an already-covered minor, e.g. `2.3.30`):

1. Add the version string to `scripts/supported-kotlin-versions.txt`
   (latest-first ordering).
2. Verify `./gradlew :debuggable-compiler:test -Ptest.kotlin=2.3.30 --no-daemon`
   passes locally.
3. Run `./scripts/smoke-test.sh 2.3.30` to build `integration-test/cmp` against
   the new target.
4. Commit with a message like `ci: add Kotlin 2.3.30 to the support matrix`.
   CI will pick it up on its next run.

Adding a new minor (e.g. `2.4.0` final):

1. Do the happy-path steps above.
2. If `compat-k23` (the current ceiling) rejects the new IR API surface, scaffold
   a `debuggable-compiler-compat-k24` module copied from `compat-k23`, register
   it in `settings.gradle.kts`, and widen the reflection fallback there until
   transformations pass.
3. Update the compat table in this file and `README.md` § "Supported Kotlin
   Versions".

Dropping an older Kotlin (rare):

1. Remove the line(s) from `scripts/supported-kotlin-versions.txt`.
2. If a `compat-kXX` module becomes unused, remove the module directory + the
   `META-INF/services/...IrInjector$Factory` entry + the `settings.gradle.kts`
   include.
3. Bump the minor version of this plugin; document the removal in the release
   notes with rationale and a suggested alternative for stuck consumers.
