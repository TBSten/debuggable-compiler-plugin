# Runtime API Design

## Annotations

### `@Debuggable`

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Debuggable(
    val isSingleton: Boolean = false
)
```

| パラメータ | 説明 |
| --- | --- |
| `isSingleton` | `true` の場合、クリーンアップを行わず永続監視する。ViewModel/AutoCloseable 以外のクラスに付与する際は必須。 |

### `@FocusDebuggable`

```kotlin
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class FocusDebuggable
```

クラス内に1つでも存在すると **Focus モード**が有効になり、このアノテーションが付いたプロパティ・メソッド**のみ**が追跡対象になる。

### `@IgnoreDebuggable`

```kotlin
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreDebuggable
```

通常モードでこのプロパティを追跡対象から除外する。

---

## `DebugCleanupRegistry`

```kotlin
class DebugCleanupRegistry : Closeable {
    fun register(cleanup: () -> Unit)
    override fun close()   // 登録された全 cleanup を実行
}
```

ViewModel の `addCloseable` や AutoCloseable の `close()` に紐付けて使用される。コンパイラが自動生成するため、ユーザが直接触ることは基本ない。

---

## 拡張関数

### `State<T>.debuggableState`

```kotlin
fun <T> State<T>.debuggableState(host: Any, name: String): State<T>
```

- `snapshotFlow` で値変化を監視し、変化のたびにログ出力する
- `host` のレジストリにクリーンアップ関数を登録する
- 元の `State<T>` をそのまま返すため、既存コードへの影響なし

### `Flow<T>.debuggableFlow`

```kotlin
fun <T> Flow<T>.debuggableFlow(host: Any, name: String): Flow<T>
```

- `onEach` で値変化を監視し、ログ出力する
- `host` のレジストリにクリーンアップ関数を登録する
- 元の `Flow<T>` をそのまま返す

---

## `logAction`

```kotlin
fun logAction(name: String, args: List<Any?>)
```

関数呼び出しをログ出力する。コンパイラが public メソッドの先頭に自動注入する。

---

## ログ出力フォーマット (暫定)

```
[Debuggable] <ClassName>.<propertyName> changed: <oldValue> → <newValue>
[Debuggable] <ClassName>.<methodName>(<arg1>, <arg2>, ...) called
```
