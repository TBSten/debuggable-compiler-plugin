# Test strategy — 3 layers (unit / compiler / smoke)

This document is the cover-matrix that task-400 asks for. It is the single
place to see "what each test layer guarantees, where they overlap, and where
the gaps are". Individual gaps are tracked as their own Phase 7 tickets.

## Layers

| Layer    | Location                                     | What it proves                                                                                   | Runtime cost |
| -------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------ | ------------ |
| unit     | `debuggable-runtime/src/commonTest`          | KMP runtime API contracts (registry, logger, Flow/State extensions). No plugin, no IR, no JVM.   | seconds      |
| compiler | `debuggable-compiler/src/test` (kctfork)     | FIR checker + IR injector → real JVM classloader → stdout assertions on `[Debuggable]` messages. | tens of sec  |
| smoke    | `scripts/smoke-test.sh` + `smoke-test-all.sh`| End-to-end `publishToMavenLocal` → consume from `integration-test/{cmp,kmp-smoke,android}` → `javap` symbol check and `jvmTest` InMemoryLogger assertion, across 17 Kotlin versions (CI matrix). | minutes      |

## Cover matrix

| Feature / Scenario                          | unit | compiler | smoke        | Gap tracked by |
| ------------------------------------------- | :--: | :------: | :----------: | -------------- |
| DebugCleanupRegistry API                    |  Y   |    Y     | partial      | —              |
| DebugLogger / PerClassLogger contract       |  Y   |    Y     | partial (JVM)| —              |
| Gradle default logger FQN resolution        |  —   |    Y     |      —       | —              |
| LogAction injection (normal fn)             |  —   |    Y     |     Y        | —              |
| LogAction on inline fn                      |  —   |    —     |      —       | task-403       |
| LogAction on suspend fn                     |  —   | compile only | —        | task-402       |
| `@Debuggable` on inner / nested class       |  —   | compile only | —        | task-401       |
| expect / actual multiplatform               |  —   |    —     |      —       | task-404       |
| ViewModel / 3-level inheritance             |  —   |  1 case  |      —       | task-405       |
| AutoCloseable `close()` that throws         |  —   |    —     |      —       | task-406       |
| IC / double-injection resilience            |  —   |    —     |      —       | task-407       |
| iOS / JS / wasm / native targets            |  —   |    —     | Y (kmp-smoke, Kotlin ≥ 2.3) | task-410 (resolved) |
| mavenLocal ↔ Central parity                 |  —   |    —     | partial (publishToMavenLocal consumed) | task-411 |
| IR dump snapshot vs bytecode assertion      |  —   | stdout only | javap-symbol | task-409 (decided: keep behavioural) |
| `enabled=false` → zero runtime in jar       |  —   |    —     | added `scripts/verify-zero-overhead.sh` | task-415 / 416 |
| Build-time delta enabled=true vs false      |  —   |    —     | added `scripts/measure-build-time.sh` (manual) | task-417 |
| Synthetic 1000-class scaling benchmark      |  —   |    —     |      —       | task-418 (DEFERRED) |
| Release-build runtime side-effects          |  —   | indirect |      —       | task-419       |

## Overlaps

- `DebugCleanupRegistry` and `DebugLogger` contracts are checked both in the
  unit layer (API only, no IR) and in the compiler layer (via generated IR).
  This overlap is intentional — the unit layer pins the runtime API, the
  compiler layer pins the IR↔runtime bridge.
- Symbol presence (`debuggableFlow`, `logAction`) is checked both in compiler
  tests (stdout log assertions) and in smoke via `javap`. The smoke `javap`
  check is weaker but catches cross-Kotlin-version regressions.

## Non-goals (intentional gaps)

- **IR dump snapshots** — we rely on behavioural JVM-execution assertions
  because bytecode-equivalent IR rewrites must not fail the suite. See
  task-409.
- **Full Central publish dry-run** — tracked in task-411; today we rely on
  `publishToMavenLocal` structural parity only.
- **1000-class synthetic benchmark** — tracked in task-418; deferred until
  task-412 / 413 show measurable hotspots.
