# Debuggable

`@Debuggable` は、Kotlin Compiler Plugin (KCP) を利用して、**Compose State** や **Coroutines Flow** の状態変化、および **ViewModel/関数内のアクション** を自動的に追跡・可視化するためのライブラリです。

面倒なログ仕込みやメモリリーク対策（監視解除）をコンパイラが肩代わりし、開発時のデバッグ体験を劇的に向上させます。

---

## 🛠 使い方 (User Guide)

### 1. インストール

Maven Central で配布しています。必要なのは 2 つ: Gradle プラグイン (コンパイラプラグインを自動で適用) と runtime ライブラリ (注入されたコードの呼び出し先)。

まず `mavenCentral()` をプラグイン解決・依存解決の両方に追加:

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
        google() // Android の場合のみ
    }
}
```

モジュールの `build.gradle.kts` でプラグインを適用し、runtime を依存に追加:

**Kotlin/JVM, Android, KMP (JVM ターゲット)**

```kotlin
plugins {
    kotlin("jvm") // もしくは kotlin("android"), kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin") version "0.1.7"
}

dependencies {
    implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.7")
}
```

**Kotlin Multiplatform (common ソースセット)**

```kotlin
plugins {
    kotlin("multiplatform")
    id("me.tbsten.debuggablecompilerplugin") version "0.1.7"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("me.tbsten.debuggablecompilerplugin:debuggable-runtime:0.1.7")
        }
    }
}
```

runtime は `jvm` / `androidTarget` / `js` / `wasmJs` / `iosArm64` / `iosSimulatorArm64` / `macosArm64` / `linuxX64` / `mingwX64` 向けに publish されています。

Android ではデフォルトで Logcat にタグ `"Debuggable"` で出力されます (`AndroidLogcatLogger`)。それ以外のプラットフォームでは `[Debuggable]` プレフィックス付きで stdout に出ます。出力先の変更方法は [§3 ロガーの差し替え](#3-ロガーの差し替え) を参照。

### 2. 基本的な使用方法
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

`Flow` / `State` 以外の普通の `var` property も追跡できます。`@FocusDebuggable` を付けると setter が書き換わり、代入のたびにログが出ます:

```kotlin
@Debuggable(isSingleton = true)
object UserForm {
    @FocusDebuggable var name: String = ""
    @FocusDebuggable var age: Int = 0
}
// UserForm.name = "daisy"   → "[Debuggable] name: daisy"
// UserForm.age  = 30        → "[Debuggable] age: 30"
```

### 3. ロガーの差し替え

デフォルトでは Android は Logcat (タグ `"Debuggable"`)、それ以外は stdout に `[Debuggable]` プレフィックス付きで出力されます。Timber、ファイル出力、テスト収集、Logcat 以外のタグなど任意の出力先に切り替えられます。
指定方法は 3 つあり、上位が優先されます。

| 優先度 | 指定方法 | 適用範囲 |
| :--- | :--- | :--- |
| 1 | `@Debuggable(logger = MyLogger::class)` | アノテーションを付けたクラス / 変数 |
| 2 | Gradle DSL `debuggable { defaultLogger.set("FQN") }` | モジュール全体 (コンパイル時固定) |
| 3 | `DefaultDebugLogger.current = DebugLogger { ... }` | プロセス全体 (ランタイム差し替え) |
| 4 | プラットフォームデフォルト (Android: Logcat / その他: stdout) | — |

ロガーに渡せるのは `DebugLogger` を実装した `object` 宣言に限られます。

```kotlin
import me.tbsten.debuggable.runtime.logging.*

// (1) 特定クラスだけ別ロガーに流す
object AuthLogger : DebugLogger {
    override fun log(message: String) = Log.d("Auth", message)
}

@Debuggable(isSingleton = true, logger = AuthLogger::class)
object AuthStore { /* ... */ }

// (2) モジュール全体を Gradle DSL で指定 — セクション 4 参照
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
| `DebugLogger.Stdout` | commonMain | `println("[Debuggable] ...")` 出力。Android 以外のデフォルト |
| `SilentLogger` | commonMain | 何も出力しない sink。プラグインを無効化せず出力だけ止めたい場合に |
| `PrefixedLogger(prefix, delegate)` | commonMain | プレフィックスを追加して別の `DebugLogger` に委譲 |
| `InMemoryLogger()` | commonMain | メッセージを list に記録。単体テストで `logger.messages` を assert する用途 |
| `CompositeLogger(vararg loggers)` | commonMain | 複数の sink にメッセージを fan-out (例: stdout + InMemory を同時に) |
| `FileLogger(file, append)` | jvm + androidMain | `java.io.File` に 1 行ずつ append。thread-safe、親ディレクトリ自動作成、write 毎に flush |
| `AndroidLogcatLogger` / `AndroidLogcatLogger(tag)` | androidMain | `Log.d(tag, message)` に流す。デフォルトタグは `"Debuggable"`。Android のデフォルト |

**アプリ内ログビューア** (オプションモジュール `debuggable-ui`):

```kotlin
implementation("me.tbsten.debuggablecompilerplugin:debuggable-ui:0.1.7")
```

```kotlin
val uiLogger = remember { UiDebugLogger(bufferSize = 500) }
DisposableEffect(uiLogger) {
    val prev = DefaultDebugLogger.current
    DefaultDebugLogger.current = uiLogger
    onDispose { DefaultDebugLogger.current = prev }
}
DebuggableLogViewer(uiLogger, modifier = Modifier.fillMaxSize())
```

`UiDebugLogger` はリングバッファ (デフォルト 1000 件) を `StateFlow` として公開し、`DebuggableLogViewer` は Compose Multiplatform で描画する Composable です。部分一致フィルタと auto-scroll を標準装備。現状は `jvm` + `androidTarget` のみ対応 (他 KMP target は別 chapter)。

### 4. Configuration

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

### 5. 内部メカニズム

プラグインがコンパイル時に注入する内容のざっくりした全体像です。`@Debuggable` を使うだけなら知らなくても良いですが、挙動に疑問があるときはこのセクションが手がかりになります。

#### 5.1 クリーンアップ戦略の注入
クラスの型を判定し、実行時の監視解除ロジックを注入します。
* **ViewModel / AutoCloseable:** `DebugCleanupRegistry` を private backing field として追加 (インライン初期化) し、既存の `close()` 本体を `try { ...元の処理... } finally { registry.close() }` でラップします。ViewModel の場合、Lifecycle 2.7 以降 `ViewModel` は `AutoCloseable` を実装するため、`onCleared()` のタイミングで発火します。プレーンな `AutoCloseable` では明示的な `close()` 時に発火します。`close()` を override せず継承しているだけのクラスには、silent leak を避けるためコンパイル時警告を出します。
* **Local Variable:** `@Debuggable` ローカル変数以降の関数本体を `try { ... } finally { registry.close() }` でラップするように再構成し、関数の全ての exit 経路でクリーンアップを実行します。

#### 5.2 プロパティのラップ
対象となる `State` や `Flow` の初期化式を、Runtimeライブラリが提供するデバッグ用関数でラップします。ラッパーは registry の coroutine スコープで監視コルーチンを起動し、元の state/flow を変更せず返します。

```kotlin
// 変換前
val uiState = mutableStateOf(UiState())

// IR変換後 (概念)
val uiState = mutableStateOf(UiState())
    .debuggableState(name = "uiState", registry = $$debuggable_registry, logger = DefaultDebugLogger)
```

#### 5.3 型ベースのインテリジェント判定
コンパイラはプロパティの完全修飾クラス名 (FQDN) をチェックし、追跡可能な型かどうかを判定します。
* `androidx.compose.runtime.State` / `MutableState`
* `kotlinx.coroutines.flow.Flow` / `StateFlow` / `MutableStateFlow`
  これら以外の型に `@FocusDebuggable` 等が付与されている場合は、コンパイル時に警告を出力します。

#### 5.4 リリース時のゼロ・オーバーヘッド
Gradleプラグイン側で `enabled.set(false)` に設定されている場合（デフォルトでは Release ビルド）、KCP は IR 変換を一切行いません。本番環境のバイナリにはデバッグ用のコードや Runtime ライブラリの依存が一切残らないため、パフォーマンスへの影響はありません。

---

### 6. リリースビルドとプライバシー

> **⚠️ 重要:** デフォルト設定では、追跡対象の `State` / `Flow` の値、および annotated action の全引数を `toString()` でログ出力します。トークン・パスワード・メールアドレスなどの個人情報が混入し得ます。**トレードオフを理解したうえでのみ、プロダクションビルドで Debuggable を有効にしてください。**

推奨設定:

```kotlin
// 多くのアプリ向け — Debuggable は debug 系ビルドのみ
debuggable {
    enabled.set(
        providers.gradleProperty("debuggable.enabled")
            .map { it.toBoolean() }
            .orElse(project.findProperty("buildType") != "release"),
    )
}
```

```kotlin
// 代替 — プラグインは有効のまま、出力だけ release で silence させる。
// (try-finally / AutoCloseable 等の cleanup 注入は残したいケース向け)
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

プロパティ単位 / クラス単位の除外:

- 単一の `State` / `Flow` を除外: `@IgnoreDebuggable`
- 機密値を redact してから forward する logger: `@Debuggable(logger = MyLogger::class)`
- 「この property だけ追跡」モード: `@FocusDebuggable`

R8 / ProGuard: runtime AAR は `META-INF/proguard/debuggable-runtime.pro` に consumer rules を同梱しています。Android の consumer 側で追加の keep rule は不要です。

---

## 🔍 仕組み

プラグインが自動で面倒を見てくれる振る舞い。ログがいつ出るのか / 何が追跡対象になるのかを予測するのに役立ちます。

### 1. メモリ管理とクリーンアップ
監視のライフサイクルはクラスの性質に応じて自動的に決定されます。

| 対象 | 条件 | クリーンアップのタイミング |
| :--- | :--- | :--- |
| **ViewModel** | `ViewModel` を継承 | `onCleared()` (Lifecycle 2.7 以降 `ViewModel` が実装する `AutoCloseable.close()` 経由) |
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

### 2. トラッキングのフィルタリング
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

---

## 🧪 サンプル

[`integration-test/`](integration-test/) 配下に、`mavenLocal()` からプラグインを取得する実行可能なサンプルを2つ用意しています。

### 1. ローカルに publish

リポジトリのルートで:

```bash
./gradlew publishToMavenLocal
```

これで `debuggable-runtime` / `debuggable-compiler` / `debuggable-gradle` (version `0.1.7`) が `~/.m2/` にインストールされます。

### 2. サンプルを選ぶ

| サンプル | ターゲット | ライフサイクルパターン | README |
|---------|-----------|------------------------|--------|
| [`integration-test/cmp`](integration-test/cmp) | Compose Multiplatform Desktop (JVM) | `@Debuggable(isSingleton = true) object` | [cmp/README.md](integration-test/cmp/README.md) |
| [`integration-test/android`](integration-test/android) | Android アプリ | `@Debuggable class : ViewModel(), AutoCloseable` | [android/README.md](integration-test/android/README.md) |

それぞれの README に起動方法・何をクリックするか・`@Debuggable` がソースのどこに付いているかが書かれています。サイドバイサイドの概要は [`integration-test/README.md`](integration-test/README.md) を参照してください。

### 3. マトリクス全体をローカルで実行 (任意)

検証スクリプトは並列モードに対応しており、ローカルマシンで CI のマトリクスを再現できます。

```bash
./scripts/smoke-test-all.sh              # 並列 (デフォルト: min(nproc/2, 4))
./scripts/smoke-test-all.sh --serial     # 1 バージョンずつ直列実行 (デバッグしやすい)
./scripts/smoke-test-all.sh --parallel 6 # ワーカー数を指定
DEBUGGABLE_PARALLEL=6 ./scripts/test-all.sh
```

各ワーカーは rsync で作成したプロジェクトコピー (`.local/tmp/…`) 内で Gradle を実行するため、`build/` の衝突は発生しません。バージョンごとのログは `.local/tmp/{smoke,test}-all-<version>.log` に出力されます。

---

## 🧷 サポート Kotlin バージョン

**Kotlin 2.0.0 以降の全 stable パッチ + 最新 Beta** を完全サポート。
`scripts/smoke-test-all.sh` (integration-test ビルド) と `scripts/test-all.sh`
(`:debuggable-compiler:test` の kctfork) で検証済み:

| Kotlin version | 状態 |
|:---|:---|
| 2.4.0-Beta1 | ✅ 検証済み |
| 2.3.21-RC2  | ✅ 検証済み |
| 2.3.20      | ✅ 検証済み (プラグインの build 固定版) |
| 2.3.10      | ✅ 検証済み |
| 2.3.0       | ✅ 検証済み |
| 2.2.21      | ✅ 検証済み |
| 2.2.20      | ✅ 検証済み |
| 2.2.10      | ✅ 検証済み |
| 2.2.0       | ✅ 検証済み |
| 2.1.21      | ✅ 検証済み |
| 2.1.20      | ✅ 検証済み |
| 2.1.10      | ✅ 検証済み |
| 2.1.0       | ✅ 検証済み |
| 2.0.21      | ✅ 検証済み |
| 2.0.20      | ✅ 検証済み |
| 2.0.10      | ✅ 検証済み |
| 2.0.0       | ✅ 検証済み |

### マルチバージョン対応の内部仕組み

IR 変換ロジックは metro 方式の per-Kotlin-version compat layer に分離してあり、
単一の plugin artifact が consumer の Kotlin コンパイラに合わせて実装を選びます:

| Module | `minVersion` | 対象コンパイラ | 備考 |
|:---|:---|:---|:---|
| `debuggable-compiler-compat-k2000` | 2.0.0  | 2.0.10 | `builders.kt` 分離前。`createDiagnosticReporter` の戻り値型が `IrMessageLogger` |
| `debuggable-compiler-compat-k2020` | 2.0.20 | 2.0.21 | 2.1.20 未満 IR API (`putValueArgument` / `extensionReceiver=` / `valueParameters`) |
| `debuggable-compiler-compat-k21`   | 2.1.20 | 2.1.21 | 新 arg/receiver API 使用。ただし `irCall` 等は `IrBuilderWithScope` のまま |
| `debuggable-compiler-compat-k23`   | 2.2.0  | 2.3.20 | `IrBuilder.irCall` + `arguments[param]=` 新 API |

ランタイムで `IrInjectorLoader` (`debuggable-compiler/compat/`) が `ServiceLoader` で
`IrInjector.Factory` を列挙し、link できない factory (例: 2.0.x で `k23` を読もうとすると
`IrBuilder.irCall` が無い) を skip、`minVersion ≤ 現コンパイラ` のうち最大のものを選択。
2.4.0-Beta1 用の `FirExtensionRegistrarAdapter` / `getAnnotation` シグネチャ変化は範囲が狭いので
`compat-k23` 内部の reflection helper で吸収。

CI マトリクスは `.github/workflows/ci.yml` の 17 バージョンを参照。
