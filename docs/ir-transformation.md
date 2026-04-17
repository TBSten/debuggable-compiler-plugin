# IR Transformation Design

コンパイル時に `IrGenerationExtension` を通じて以下の変換を行う。

---

## 1. クラスレベル変換

`@Debuggable` が付与されたクラスの型を判定し、クリーンアップ戦略を注入する。

### ViewModel

```
判定: androidx.lifecycle.ViewModel を継承しているか
```

**変換内容:** `init` ブロック末尾に以下を注入

```kotlin
// 注入されるコード (概念)
init {
    val $$debugRegistry = DebugCleanupRegistry()
    this.addCloseable("__debuggable__", $$debugRegistry)
}
```

### AutoCloseable

```
判定: java.lang.AutoCloseable を実装しているか
```

**変換内容:**
1. `$$debuggable_registry: DebugCleanupRegistry` プロパティを追加
2. `close()` メソッド末尾に `$$debuggable_registry.close()` を注入

### その他 (isSingleton = true)

クリーンアップは不要。レジストリ注入なし。`isSingleton = false` の場合はコンパイルエラー。

---

## 2. プロパティレベル変換 (2-pass)

### Pass 1: モード判定

クラス内のプロパティを走査し、`@FocusDebuggable` が1つでもあれば **Focus モード**、なければ**通常モード**。

### Pass 2: フィルタリングとラップ

| モード | 対象プロパティ |
| --- | --- |
| Focus モード | `@FocusDebuggable` が付いたもの**のみ** |
| 通常モード | `@IgnoreDebuggable` が付いていないもの |

対象プロパティの型が `State` / `Flow` 系列の場合、初期化式をラップする。

```kotlin
// 変換前
val uiState = mutableStateOf(UiState())

// 変換後 (概念)
val uiState = mutableStateOf(UiState()).debuggableState(this, "uiState")
```

**追跡対象の型 (FQDN):**
- `androidx.compose.runtime.State`
- `androidx.compose.runtime.MutableState`
- `kotlinx.coroutines.flow.Flow`
- `kotlinx.coroutines.flow.StateFlow`
- `kotlinx.coroutines.flow.MutableStateFlow`

---

## 3. 関数レベル変換

`@Debuggable` クラス内の `public` メソッドの先頭に `logAction` を注入する。

```kotlin
// 変換前
fun onSearchClicked(query: String) {
    // ...
}

// 変換後
fun onSearchClicked(query: String) {
    logAction("onSearchClicked", listOf(query))
    // ...
}
```

---

## 4. ローカル変数変換

関数内の `@Debuggable val x = ...` に対して、関数ボディを `try-finally` でラップする。

```kotlin
// 変換前
fun performTask() {
    @Debuggable val tempState = mutableStateOf("Pending")
    doWork(tempState)
}

// 変換後 (概念)
fun performTask() {
    val $$debugRegistry = DebugCleanupRegistry()
    val tempState = mutableStateOf("Pending").debuggableState($$debugRegistry, "tempState")
    try {
        doWork(tempState)
    } finally {
        $$debugRegistry.close()
    }
}
```

---

## 5. バリデーション

| 条件 | 結果 |
| --- | --- |
| `@FocusDebuggable` と `@IgnoreDebuggable` を同一プロパティに付与 | **Compilation Error** |
| `State`/`Flow` 以外の型に `@FocusDebuggable` を付与 | **Compilation Warning** |
| ViewModel/AutoCloseable 以外 かつ `isSingleton = false` のクラスに `@Debuggable` | **Compilation Error** |

---

## 6. Release ビルド時の動作

Gradle Plugin で `enabled = false` の場合、`IrGenerationExtension` は何もせずに返る。変換コードは一切生成されない。
