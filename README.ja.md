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

### 5. Gradle DSL
Gradle プラグインは機能単位のトグルを提供しており、プラグイン全体を無効化せず特定の計測だけを個別に OFF にできます。

```kotlin
debuggable {
    enabled.set(true)        // マスタースイッチ（デフォルト: true）
    observeFlow.set(true)    // Flow/State イニシャライザのラップ（デフォルト: true）
    logAction.set(true)      // public メソッド呼び出しのログ（デフォルト: true）
}
```

`enabled = false` の場合プラグインは完全な no-op になります。`observeFlow` と `logAction` を個別に無効化すると、その変換だけがスキップされ他の機能はそのまま動作します。

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