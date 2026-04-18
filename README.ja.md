# Debuggable

`@Debuggable` は、Kotlin Compiler Plugin (KCP) を利用して、**Compose State** や **Coroutines Flow** の状態変化、および **ViewModel/関数内のアクション** を自動的に追跡・可視化するためのライブラリです。

面倒なログ仕込みやメモリリーク対策（監視解除）をコンパイラが肩代わりし、開発時のデバッグ体験を劇的に向上させます。

---

## 🛠 使い方 (User Guide)

### 1. 基本的な使用方法
`@Debuggable` をクラスに付与するだけで、その中の `State` と `Flow` が自動的にトラッキングされます。

```kotlin
@Debuggable
class SearchViewModel : ViewModel() {
    // 自動的に値の変化がログ出力され、ViewModel破棄時に監視も解除される
    val searchQuery = MutableStateFlow("")
    val uiState = mutableStateOf(UiState())

    // 関数の呼び出し（アクション）も引数と共に自動記録
    fun onSearchClicked(query: String) { ... }
}
```

### 2. メモリ管理とクリーンアップ
監視のライフサイクルはクラスの性質に応じて自動的に決定されます。

| 対象 | 条件 | クリーンアップのタイミング |
| :--- | :--- | :--- |
| **ViewModel** | `ViewModel` を継承 | `onCleared()` (内部で `addCloseable` を利用) |
| **AutoCloseable** | `AutoCloseable` を実装 | `close()` メソッドの実行時 |
| **Singleton** | `isSingleton = true` | なし（プロセス終了まで永続） |
| **Local Variable** | 関数内の変数 | 関数の実行終了時（Scope Exit） |

```kotlin
// 関数内での一時的な監視
fun performTask() {
    @Debuggable
    val tempState = mutableStateOf("Pending")
    // 関数を抜ける際に自動でクリーンアップされる
}

// シングルトンとして扱う場合
@Debuggable(isSingleton = true)
class GlobalSettings { ... }
```

### 3. トラッキングのフィルタリング
特定のプロパティだけに注目したり、ノイズを除外したりできます。

```kotlin
@Debuggable
class ComplexViewModel : ViewModel() {
    // @Focus がある場合、これ "だけ" が追跡対象になる（Focusモード）
    @FocusDebuggable
    val targetState = mutableStateOf(0)

    // 通常モード時、これをつけると追跡から除外される
    @IgnoreDebuggable
    val noiseState = mutableStateOf("")
}
```

### 4. ロガーの差し替え

デフォルトでは `@Debuggable` は `[Debuggable]` プレフィックス付きで標準出力にログを出します。
Android Logcat、Timber、ファイル出力、テスト収集など任意の出力先に切り替えられます。
指定方法は 3 つあり、上位が優先されます。

| 優先度 | 指定方法 | 適用範囲 |
| :--- | :--- | :--- |
| 1 | `@Debuggable(logger = MyLogger::class)` | アノテーションを付けたクラス / 変数 |
| 2 | Gradle DSL `debuggable { defaultLogger.set("FQN") }` | モジュール全体 (コンパイル時固定) |
| 3 | `DefaultDebugLogger.current = DebugLogger { ... }` | プロセス全体 (ランタイム差し替え) |
| 4 | `DebugLogger.Stdout` (組み込みのフォールバック) | — |

ロガーに渡せるのは `DebugLogger` を実装した `object` 宣言に限られます。

```kotlin
import me.tbsten.debuggable.runtime.logging.*

// (1) 特定クラスだけ別ロガーに流す
object AuthLogger : DebugLogger {
    override fun log(message: String) = Log.d("Auth", message)
}

@Debuggable(isSingleton = true, logger = AuthLogger::class)
object AuthStore { /* ... */ }

// (2) モジュール全体を Gradle DSL で指定 — セクション 5 参照
// (3) アプリ起動時にプロセス全体を差し替える
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DefaultDebugLogger.current = AndroidLogcatLogger   // 組み込みロガー
    }
}
```

**組み込みロガー** (`debuggable-runtime` に同梱):

| ロガー | ソースセット | 説明 |
| :--- | :--- | :--- |
| `DebugLogger.Stdout` | commonMain | デフォルトの `println("[Debuggable] ...")` 出力 |
| `SilentLogger` | commonMain | 何も出力しない sink。プラグインを無効化せず出力だけ止めたい場合に |
| `PrefixedLogger(prefix, delegate)` | commonMain | プレフィックスを追加して別の `DebugLogger` に委譲 |
| `AndroidLogcatLogger` / `AndroidLogcatLogger(tag)` | androidMain | `Log.d(tag, message)` に流す。デフォルトタグは `"Debuggable"` |

### 5. Gradle DSL の設定

Gradle プラグインは機能単位のトグルとコンパイル時デフォルトロガーを提供し、プラグイン全体を
無効化せず特定の計測を個別に OFF / 差し替えできます。

```kotlin
debuggable {
    enabled.set(true)          // マスタースイッチ（デフォルト: true）
    observeFlow.set(true)      // Flow/State イニシャライザのラップ（デフォルト: true）
    logAction.set(true)        // public メソッド呼び出しのログ（デフォルト: true）
    defaultLogger.set("")      // モジュール全体の default として使う DebugLogger object の FQN
                               // (空文字 = DefaultDebugLogger。その場合はランタイムで差し替え可能)
                               // 例: defaultLogger.set("com.example.myapp.MyDebugLogger")
}
```

`enabled = false` の場合プラグインは完全な no-op になり、IR 変換も行わず Runtime 依存も残りません。
`observeFlow` / `logAction` を個別に無効化すると、その変換だけがスキップされ他の機能はそのまま動作します。

---

## 🏗 内部メカニズム (Internal IR Transformation)

本プラグインはコンパイル時に **Kotlin IR (Intermediate Representation)** を書き換えることで、ソースコードには現れないデバッグ用の配線を自動構築します。

### 1. クリーンアップ戦略の注入
クラスの型を判定し、実行時の監視解除ロジックを注入します。
* **ViewModel:** `init` ブロックで `DebugCleanupRegistry` を生成し、`addCloseable` に登録するコードを生成します。
* **AutoCloseable:** `close()` メソッドをフックし、その末尾に登録されたクリーンアップ関数をすべて実行するループを挿入します。
* **Local Variable:** 関数全体を `try-finally` ブロックでラップするように再構成し、`finally` 内でクリーンアップを実行します。

### 2. プロパティのラップ
対象となる `State` や `Flow` の初期化式を、Runtimeライブラリが提供するデバッグ用関数でラップします。

```kotlin
// 変換前
val uiState = mutableStateOf(UiState())

// IR変換後 (概念)
val uiState = mutableStateOf(UiState()).also { 
    debuggableState(host = this, name = "uiState", state = it) 
}
```

### 3. 型ベースのインテリジェント判定
コンパイラはプロパティの完全修飾クラス名 (FQDN) をチェックし、追跡可能な型かどうかを判定します。
* `androidx.compose.runtime.State` / `MutableState`
* `kotlinx.coroutines.flow.Flow` / `StateFlow` / `MutableStateFlow`
  これら以外の型に `@FocusDebuggable` 等が付与されている場合は、コンパイル時に警告を出力します。

### 4. リリース時のゼロ・オーバーヘッド
Gradleプラグイン側で `enabled.set(false)` に設定されている場合（デフォルトでは Release ビルド）、KCP は IR 変換を一切行いません。本番環境のバイナリにはデバッグ用のコードや Runtime ライブラリの依存が一切残らないため、パフォーマンスへの影響はありません。

---

## 🧪 サンプル

[`integration-test/`](integration-test/) 配下に、`mavenLocal()` からプラグインを取得する実行可能なサンプルを2つ用意しています。

### 1. ローカルに publish

リポジトリのルートで:

```bash
./gradlew publishToMavenLocal
```

これで `debuggable-runtime` / `debuggable-compiler` / `debuggable-gradle` (version `0.1.0`) が `~/.m2/` にインストールされます。

### 2. サンプルを選ぶ

| サンプル | ターゲット | ライフサイクルパターン | README |
|---------|-----------|------------------------|--------|
| [`integration-test/cmp`](integration-test/cmp) | Compose Multiplatform Desktop (JVM) | `@Debuggable(isSingleton = true) object` | [cmp/README.md](integration-test/cmp/README.md) |
| [`integration-test/android`](integration-test/android) | Android アプリ | `@Debuggable class : ViewModel(), AutoCloseable` | [android/README.md](integration-test/android/README.md) |

それぞれの README に起動方法・何をクリックするか・`@Debuggable` がソースのどこに付いているかが書かれています。サイドバイサイドの概要は [`integration-test/README.md`](integration-test/README.md) を参照してください。

---

## 🧷 サポート Kotlin バージョン

本プラグインは **Kotlin 2.2.20 以上** をサポートラインとしています。
`integration-test/cmp` の smoke matrix で検証済み:

| Kotlin version | 状態 |
|:---|:---|
| 2.4.0-Beta1 | ✅ 検証済み |
| 2.3.21-RC2  | ✅ 検証済み |
| 2.3.20      | ✅ 検証済み (ピン留め baseline) |
| 2.3.10      | ✅ 検証済み |
| 2.3.0       | ✅ 検証済み |
| 2.2.21      | ✅ 検証済み |
| 2.2.20      | ✅ 検証済み |
| 2.2.10      | ✅ 検証済み |
| 2.2.0       | ✅ 検証済み |
| 2.0 / 2.1   | ⚠️ 未対応 (下記参照) |

### なぜ 2.0 / 2.1 は未対応か

`debuggable-runtime` は transitive 依存として `kotlinx-coroutines-core:1.10.x`
(metadata `[2.1.0]`) を持ち込みます。古い compiler (2.0.x / 2.1.x) は新しい metadata の
クラスを拒否し、さらに 2.1.21 compiler には `FirIncompatibleClassExpressionChecker` の
バグがあり null source で crash します。古い coroutines に下げる or per-compiler-version
の runtime artifact を publish するには大幅な工数が必要なため、一旦保留としています。
詳細は `.local/tickets/002-runtime-binary-compat-2.0-2.1.md` (リポジトリ内) を参照。

### マルチバージョン対応の内部仕組み

プラグインの JAR は `kotlin-compiler-embeddable:2.3.20` でコンパイルしていますが、
2 つの軽量 reflection helper が API 差異を吸収することで、同じ JAR が 2.2.20 〜
2.4.0-Beta1 で動きます:

- `registerExtensionCompat` — Kotlin 2.4 で `FirExtensionRegistrarAdapter.Companion` /
  `IrGenerationExtension.Companion` の parent class が `ProjectExtensionDescriptor` から
  `ExtensionPointDescriptor` に変更されたので、runtime で適切な
  `ExtensionStorage.registerExtension(descriptor, extension)` overload を見つけて invoke
- `getAnnotationCompat` — Kotlin 2.4 で `IrUtilsKt.getAnnotation` の return type が
  `IrConstructorCall?` から `IrAnnotation?` に変わったので、API 安定型 (`IrConstructorCall`)
  だけを使って `annotations` を手動 iterate

`kctfork` ベースの fine-grained テスト (`debuggable-compiler/src/test/kotlin/...`) は
プラグイン自身の build Kotlin に pin 留めし、cross-version の正しさは
`scripts/smoke-test-all.sh` で検証 (CI matrix は `.github/workflows/ci.yml` 参照)。