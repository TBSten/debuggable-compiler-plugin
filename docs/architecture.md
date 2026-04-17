# Implementation Specification: Debuggable Kotlin Compiler Plugin

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
    - `DebugCleanupRegistry`: `Closeable` を実装。内部に `MutableList<() -> Unit>` を保持し、`close()` 時にすべて実行。
    - `fun <T> State<T>.debuggableState(host: Any, name: String): State<T>`: `snapshotFlow` を用いて値を監視し、クリーンアップ関数を `host` のレジストリに登録する。
    - `fun <T> Flow<T>.debuggableFlow(host: Any, name: String): Flow<T>`: `onEach` 等で値を監視し、クリーンアップ関数を `host` のレジストリに登録する。
    - `fun logAction(name: String, args: List<Any?>)`: 関数呼び出しをログ出力する。

### 3.2 `:debuggable-compiler`
`IrGenerationExtension` を用い、以下の IR 変換を実装してください。

#### Class-level Transformation
`@Debuggable` が付与されたクラスに対し、特性に応じて以下を注入します。
1. **ViewModel (androidx.lifecycle.ViewModel):**
    - `init` ブロックで `DebugCleanupRegistry` を生成し、`this.addCloseable("DEBUG_KEY", registry)` を注入。
2. **AutoCloseable:**
    - 隠しプロパティ `$$debuggable_cleanups` (List) を生成。
    - `close()` メソッドの末尾に、リスト内の関数を実行するコードを注入。
3. **Others:** `isSingleton = true` でない場合はコンパイルエラーを出力。

#### Property-level Transformation (2-pass scan)
1. **Scan:** クラス内に `@FocusDebuggable` があるか判定。
2. **Filter:** Focusモードなら `@Focus` 付きのみ、通常モードなら `@Ignore` 以外を対象とする。
3. **Wrap:** 型が `State` または `Flow` 系列の場合、初期化式をラップ。
    - 例: `val uiState = mutableStateOf(0)` → `val uiState = mutableStateOf(0).debuggableState(this, "uiState")`

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
