# Module Structure

## Overview

```
debuggable-compiler-plugin/
├── debuggable-runtime/       # App dependency (lightweight)
├── debuggable-compiler/      # Kotlin Compiler Plugin
└── debuggable-gradle/        # Gradle Plugin
```

## `:debuggable-runtime`

アプリが直接依存する軽量ライブラリ。デバッグ用アノテーションと実行時サポートを提供する。

**依存関係:**
- `kotlin-stdlib`
- `androidx.compose.runtime` (compileOnly — ユーザのアプリ側で提供)
- `kotlinx-coroutines-core` (compileOnly)

**パッケージ構成:**
```
me.tbsten.debuggable.runtime/
├── annotations/
│   ├── Debuggable.kt
│   ├── FocusDebuggable.kt
│   └── IgnoreDebuggable.kt
├── registry/
│   └── DebugCleanupRegistry.kt
└── extensions/
    ├── StateExtensions.kt
    └── FlowExtensions.kt
```

## `:debuggable-compiler`

IR 変換の本体。Kotlin Compiler Plugin API を使用。

**依存関係:**
- `kotlin-compiler-embeddable`
- `:debuggable-runtime` (シンボル参照のため)

**パッケージ構成:**
```
me.tbsten.debuggable.compiler/
├── DebuggableIrGenerationExtension.kt   # エントリポイント
├── DebuggableComponentRegistrar.kt
├── visitors/
│   ├── ClassTransformer.kt              # クラスレベル変換
│   ├── PropertyTransformer.kt           # プロパティラップ
│   ├── FunctionTransformer.kt           # logAction 注入
│   └── LocalVariableTransformer.kt      # try-finally ラップ
└── util/
    ├── TypeChecker.kt                   # State/Flow FQDN 判定
    └── IrBuilderExtensions.kt           # IR 生成ヘルパー
```

## `:debuggable-gradle`

Gradle Plugin。ビルドタイプ別の有効/無効制御とコンパイラプラグインの配線を担う。

**依存関係:**
- `kotlin-gradle-plugin-api`
- `:debuggable-compiler`

**パッケージ構成:**
```
me.tbsten.debuggable.gradle/
├── DebuggableGradlePlugin.kt            # Plugin エントリポイント
└── DebuggableExtension.kt               # DSL 拡張 (enabled フラグ等)
```

**Gradle DSL:**
```kotlin
debuggable {
    enabled.set(true)   // デフォルト: Debug=true, Release=false
}
```

## モジュール間依存関係

```
App
 ├── depends on → :debuggable-runtime
 └── applies   → :debuggable-gradle
                      └── configures → :debuggable-compiler
                                            └── references → :debuggable-runtime
```
