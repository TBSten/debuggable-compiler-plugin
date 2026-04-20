# Implementation Specification: Debuggable Kotlin Compiler Plugin

> **Note**: 本ドキュメントは初期設計時のスペックから実装が進むにつれて一部乖離が生じている。最新の挙動と公開 API は `README.md` / `README.ja.md` § 5 と `docs/runtime-api.md` を正とする。以下は設計意図の参考資料。

## 1. Project Overview
Composeの `State` と Coroutines の `Flow` の状態変化、および関数の実行（Action）を自動的に追跡・記録する Kotlin Compiler Plugin です。
コンパイル時に IR (Intermediate Representation) を書き換え、デバッグ用コードの注入と自動メモリ管理（監視解除）を実現します。

## 2. Project Structure
- **`:debuggable-runtime`**: アプリが依存する軽量ライブラリ。
- **`:debuggable-compiler`**: Kotlin Compiler Plugin 本体。IR 変換ロジックを実装。
- **`:debuggable-gradle`**: Gradle Plugin。ビルドタイプ（Debug/Release）ごとの有効/無効を制御。

---

## 3. Module Details

### 3.1 `:debuggable-runtime`
以下のコンポーネントを実装してください。

- **Annotations:**
    - `@Debuggable(isSingleton: Boolean = false)` (Target: Class, LocalVariable, Function)
    - `@FocusDebuggable` (Target: Property)
    - `@IgnoreDebuggable` (Target: Property)
- **Registry & Logic:**
    - `DebugCleanupRegistry`: `AutoCloseable` を実装。`coroutineScope: CoroutineScope` を公開し、`close()` 時に scope.cancel + 登録済みクリーンアップ関数を実行。シングルトン用 `DebugCleanupRegistry.Default` は no-op かつ永続 scope を公開する。
    - `fun <T> State<T>.debuggableState(name: String, registry: DebugCleanupRegistry = DebugCleanupRegistry.Default, logger: DebugLogger = DefaultDebugLogger): State<T>`: `snapshotFlow` で値変化を監視し、`registry.coroutineScope` 内にコルーチンを起動する。元の State をそのまま返す。
    - `fun <T> Flow<T>.debuggableFlow(name: String, registry: DebugCleanupRegistry = DebugCleanupRegistry.Default, logger: DebugLogger = DefaultDebugLogger): Flow<T>`: 同様に `onEach` ベースで監視する。
    - 関数呼び出しログはランタイム関数ではなく、`DebugLogger.log(String)` を IR injector が直接組み立てて呼ぶ。

### 3.2 `:debuggable-compiler`
`IrGenerationExtension` を用い、以下の IR 変換を実装してください。

#### Class-level Transformation
`@Debuggable` が付与されたクラスに対し、特性に応じて以下を注入します。
1. **ViewModel (androidx.lifecycle.ViewModel) / AutoCloseable:**
    - private backing field `$$debuggable_registry: DebugCleanupRegistry` をインライン初期化で追加。
    - 既存の `close()` 本体を `try { ...original... } finally { $$debuggable_registry.close() }` でラップ。ViewModel は Lifecycle 2.7 以降 AutoCloseable を実装するため、`onCleared()` で自動発火する。
    - `close()` を override していない場合はコンパイル時警告。
2. **Singleton (`isSingleton = true`):**
    - `DebugCleanupRegistry.Default` (no-op) を参照し、cleanup は行わない。
3. **Local Variable:**
    - 後述の Local Variable Transformation を適用。

#### Property-level Transformation (2-pass scan)
1. **Scan:** クラス内に `@FocusDebuggable` があるか判定。
2. **Filter:** Focusモードなら `@Focus` 付きのみ、通常モードなら `@Ignore` 以外を対象とする。
3. **Wrap:** 型が `State` または `Flow` 系列の場合、初期化式をラップ。
    - 例: `val uiState = mutableStateOf(0)` → `val uiState = mutableStateOf(0).debuggableState(name = "uiState", registry = $$debuggable_registry, logger = DefaultDebugLogger)`

#### Local Variable Transformation
- 関数内の `@Debuggable val x = ...` に対し、関数ボディを `try-finally` でラップ。
- `finally` ブロックで `x` に紐づく監視解除処理を実行するように IR を再構成。

#### Function-level Transformation
- 対象クラス内の public メソッドの先頭に `logAction(methodName, args)` を注入。

---

## 4. Implementation Steps (for AI)

1. **Phase 1: Runtime Setup**
    - アノテーション、レジストリ、および `State/Flow` 拡張関数の作成。
2. **Phase 2: Compiler Plugin Scaffolding**
    - `IrElementTransformerVoid` を用いた Visitor の実装。
    - プロパティの型（FQDN）判定ロジックの実装。
3. **Phase 3: IR Injection Logic**
    - クラスへのプロパティ/メソッド注入。
    - `try-finally` 構造の構築。
    - `this` シンボルの適切なハンドリング。
4. **Phase 4: Gradle Integration & Testing**
    - `KotlinCompilerPluginSupportExtension` の実装。
    - `Kotlin Compile Testing` による、コンパイル後の挙動確認テストの作成。

---

## 5. Constraints & Errors
- 同一プロパティへの `@Focus` と `@Ignore` の重複使用は **Compilation Error**。
- `State/Flow` 以外の型への `@Focus` 付与は **Compilation Warning**。
- `ViewModel/AutoCloseable` 以外、かつ非シングルトンのクラスへの `@Debuggable` は **Compilation Error**。
- Release ビルド時は、パフォーマンス維持のため全ての変換スキップを保証すること。

---

## 6. Trust boundary (external input)

The plugin accepts two kinds of "external" input at build time. Both are
treated as trusted because they are supplied by the consumer's own Gradle
build — the threat model assumes the build script itself is trusted code.

- **`debuggable { defaultLogger.set("<FQN>") }`** — any FQN string is passed
  through `DebuggableCommandLineProcessor` into
  `DebuggableOptions.defaultLoggerFqn`, then resolved by each compat's
  `LoggerResolver`. Unresolved / non-object / non-`DebugLogger` FQNs raise a
  `CompilerMessageSeverity.ERROR` at IR phase and the plugin falls back to
  `DefaultDebugLogger`. Invalid FQNs therefore break the build rather than
  produce unsafe bytecode.
- **`ServiceLoader<IrInjector.Factory>` discovery** — `IrInjectorLoader`
  scans the compiler plugin classpath for `META-INF/services/` entries,
  swallows load failures (`NoClassDefFoundError` etc.) to remain tolerant of
  stale compat JARs, and selects the factory with the highest `minVersion`
  that also accepts the current runtime Kotlin version. Tie-breaking between
  factories with the same `minVersion` is currently order-dependent; this
  matters only if a third-party JAR ships its own `IrInjector.Factory`,
  which is outside the supported use case.

Consumers who need to audit the selected compat impl can set
`-Ddebuggable.compat.debug=true` on the Gradle daemon — `IrInjectorLoader`
then logs the candidate factories, their `minVersion`/`maxVersion` ranges,
and the final choice to stderr.
