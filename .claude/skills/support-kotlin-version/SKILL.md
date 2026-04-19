---
name: support-kotlin-version
description: >
  このプロジェクト (debuggable-compiler-plugin) のサポート Kotlin バージョンを
  追加・変更・削除する。Kotlin の新 patch/minor/RC/Beta を対象に、どの compat
  module に載せるか判定 → 必要なら新 compat module を足場作成 → VERSIONS テーブル
  (smoke-test-all / test-all / ci.yml / README / current.md) を同期 → kctfork と
  Compose の version マップを更新 → 両スクリプトで検証 → current.md に結果反映、
  までを一括でやる。Kotlin バージョンまわりの変更は触る場所が多く散らばっているため
  この skill が必ず起動するべき。
  Use when requested: "Kotlin 2.X.Y をサポートしたい", "Kotlin の新バージョン対応",
  "サポート Kotlin バージョン追加", "Kotlin 2.X.Y のサポートを外す",
  "kctfork を X にあげて", "新しい Kotlin RC / Beta を入れて", "integration-test を
  Kotlin 2.X.Y で動かしたい", "compat module 追加", "VERSIONS を更新",
  "current.md の Kotlin バージョンを直して".
---

# Support Kotlin Version

新しい Kotlin のパッチ / マイナー / RC / Beta をこのプロジェクトのサポート対象に入れる (または外す) ための一連の作業をカバーする。

## なぜこの skill があるか

サポート Kotlin バージョンは **11 ファイル以上** に散らばっている。1 つでも忘れると
「smoke-test は通るのに CI が matrix で fail」「unit test は通るのに compat module が
ロードされない」「README に出てない」など半端な状態を作りやすい。

| ファイル | 役割 | いつ触るか |
|---|---|---|
| `scripts/smoke-test-all.sh` の `VERSIONS` | integration build 対象 | 常に |
| `scripts/test-all.sh` の `VERSIONS` | unit test 対象 | 常に |
| `.github/workflows/ci.yml` の `unit-matrix.matrix.kotlin` | CI unit 行列 | 常に |
| `.github/workflows/ci.yml` の `integration.matrix.kotlin` | CI integration 行列 | 常に |
| `README.md` の Supported Kotlin Versions テーブル | ユーザー向け | 常に |
| `README.ja.md` の サポート Kotlin バージョンテーブル | ユーザー向け | 常に |
| `.local/multi-kotlin-versions/current.md` | 作業メモの status + 変更履歴 | 常に (**作業のたび**) |
| `debuggable-compiler/build.gradle.kts` の `kctforkForKotlin` マップ | kctfork ↔ Kotlin 対応 | 新 minor / 新 patch で kctfork が変わる時 |
| `integration-test/cmp/settings.gradle.kts` の Kotlin → Compose マップ | CMP plugin 選択 | 新 minor / 新 patch で CMP が変わる時 |
| `debuggable-compiler-compat-kXX/` 一式 | per-version IR impl | 既存 compat では API 差異を吸収できない時だけ |
| `settings.gradle.kts` + `debuggable-compiler/build.gradle.kts` の runtimeOnly | 新 compat module include | 新 compat module 作成時のみ |

## Usage

### Step 0: 要件確認

ユーザーの指示から以下を把握する (明確なら聞き直さない)。

1. **対象バージョン** — 追加 / 変更 / 削除したい Kotlin のバージョン
2. **operation** — 追加 / 削除 / 置換 (例: "2.3.22 を入れる", "2.0.0 を外す", "2.3.21-RC2 を 2.3.21 に置換")
3. **新 compat module が必要か** — 既存 compat で対応不可な API 差異があるか。判定は Step 1 で行う

### Step 1: どの compat module に載せるか判定

現状の compat module (バージョン範囲は `minVersion` で決まる):

| Module | `minVersion` | 対象 patch 範囲 (次 impl との間) | 代表的な API 境界 |
|---|---|---|---|
| `debuggable-compiler-compat-k2000` | 2.0.0 | 2.0.0 – 2.0.10 | `BuildersKt` 分離前 |
| `debuggable-compiler-compat-k2020` | 2.0.20 | 2.0.20 – 2.1.10 | 新 `BuildersKt`、legacy IR builders |
| `debuggable-compiler-compat-k21` | 2.1.20 | 2.1.20 – 2.1.21 | `IrMemberAccessExpression.arguments[param]=` など新 API |
| `debuggable-compiler-compat-k23` | 2.2.0 | 2.2.0 – (最新) | `IrBuilder.irCall`、`IrDeclarationOriginImpl("DEFINED")` |

**判定ルール:**

- 対象バージョンが `[minVersion, 次 module の minVersion)` の範囲に入れば **既存 module で OK**。
- その範囲に入っていても、`scripts/smoke-test-all.sh` や `scripts/test-all.sh` で
  `NoSuchMethodError` / `NoClassDefFoundError` / `IncompatibleClassChangeError` が
  出る場合は新しい API 差異が発生している。その時は:
  1. 小さく済ませるなら **既存 module のソース修正** (API の reflection 化 /
     直接インスタンス化で回避 — 過去実例: `IrDeclarationOrigin.DEFINED` を
     `IrDeclarationOriginImpl("DEFINED")` に置換)
  2. 回避不能なら **新 compat module を作成**

**判定が迷ったらまず既存 module で試せ**。new module はオーバーヘッドが大きい。
新 module が必要になるパターンは過去 2 回 (k2020 分離、k2000 分離) とも
「特定パッチで compile 済 class にしか存在しない API ファクトリ」を切り出すため。

### Step 2a: 既存 compat module に追加する場合の変更

ファイル修正の順番。変更前に `.local/multi-kotlin-versions/current.md` を読んで
現状テーブルを頭に入れておく。

1. **`scripts/smoke-test-all.sh`** `VERSIONS` 配列に対象バージョン追加/削除。
   既存の並び (新しい → 古い、メジャー系統ごと) を踏襲。
2. **`scripts/test-all.sh`** 同じ並びで同期。
3. **`.github/workflows/ci.yml`** `unit-matrix.strategy.matrix.kotlin` と
   `integration.strategy.matrix.kotlin` の **両方** に同じ文字列を追加。
4. **`debuggable-compiler/build.gradle.kts`** の `kctforkForKotlin` `when` 式:
   - 新 minor (例: 2.5.x) 追加 → 新しい branch を先頭に。対応 kctfork release を
     ZacSweers/kotlin-compile-testing の GitHub Releases で確認。
   - 新 patch (例: 2.3.22) → `startsWith("2.3")` に吸収されるなら変更不要。
     個別マップが要るなら `when` に追加。
   - 未リリース (RC / Beta) → 直近 stable の kctfork を流用すると決めて、
     コメントにその旨書く。
5. **`integration-test/cmp/settings.gradle.kts`** Kotlin → Compose マップ:
   - 新 minor → 対応する Compose Multiplatform のバージョンを選んで branch 追加。
     JetBrains/compose-multiplatform の Release Notes で "Supports Kotlin X.Y" の
     アナウンスを確認。
6. **`README.md`** "Supported Kotlin Versions" テーブルに 1 行追加/削除。
7. **`README.ja.md`** 同じ行を日本語側にも反映 (**両方必ず**)。
8. **`.local/multi-kotlin-versions/current.md`** の status テーブルに対象行を追加
   (status は最初 🟠 Not Tested。Step 3 の後で更新する)。

### Step 2b: 新 compat module が必要な場合

既存 module で対応不可と判断した時だけ。

1. **近い既存 module を丸ごとコピー**
   ```bash
   cp -r debuggable-compiler-compat-k2020 debuggable-compiler-compat-k{新名}
   ```
2. **パッケージ名を一括置換** (macOS BSD sed は `\b` 単語境界が動かないので注意。
   `\.` で区切る):
   ```bash
   # 例: k2020 → k3000 にリネーム
   find debuggable-compiler-compat-k3000/src -type f -name '*.kt' -print0 | \
     xargs -0 sed -i '' -e 's|\.compat\.k2020\.|.compat.k3000.|g' \
                         -e 's|\.compat\.k2020$|.compat.k3000|g'
   # ディレクトリもリネーム
   find debuggable-compiler-compat-k3000/src -type d -name 'k2020' | \
     while read d; do git mv "$d" "$(dirname "$d")/k3000"; done
   ```
   **past incident**: `k20 → k2020` のリネーム時、source 内の package 宣言を置換
   し忘れて published JAR のクラスパスが `compat/k20/` のままになり、ServiceLoader
   が誤マッチした。必ず **published JAR を unzip して META-INF と class path が
   期待どおりか確認する**。
3. **`build.gradle.kts`** 編集:
   - `mavenPublishing { pom { name / description } }` を新 module 向けに
   - `compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:<該当バージョンの最古>")`
     — このモジュールがサポートする最も古い patch に合わせるのが安全
     (新しい symbol を使わないよう強制される)
   - `apiVersion` / `languageVersion` は minVersion の major.minor に合わせる
4. **`Factory` の `minVersion`** を更新 (`DebuggableIrInjectorKXX.kt` の Factory
   ネストクラス内)。
5. **`src/main/resources/META-INF/services/me.tbsten.debuggable.compiler.compat.IrInjector$Factory`**
   の中身の FQN を新 module の Factory に書き換え。
6. **`settings.gradle.kts`** に `include(":debuggable-compiler-compat-k{新名}")` 追加。
7. **`debuggable-compiler/build.gradle.kts`** の `dependencies` に
   `runtimeOnly(project(":debuggable-compiler-compat-k{新名}"))` 追加。
8. **`README.md` / `README.ja.md`** の compat modules テーブルにも行追加。
9. Step 2a の 1–8 も並行して実行。

### Step 3: 検証

```bash
bash scripts/smoke-test-all.sh > .local/tmp/smoke-test-all-$(date +%Y%m%d-%H%M%S).log 2>&1
bash scripts/test-all.sh > .local/tmp/test-all-$(date +%Y%m%d-%H%M%S).log 2>&1
```

**両スクリプトとも対象含め全バージョン 🟢 になるまで繰り返す**。
失敗したら該当 log を見て原因特定 (`.local/tmp/smoke-{version}.log` や
`.local/tmp/test-all-{version}.log`)。

対象バージョン削除の場合は、該当 log ファイルを残さない (削除しておく)。

### Step 4: `current.md` 更新と commit

`.local/multi-kotlin-versions/current.md`:
- status テーブルの該当行を更新 (🟢 / 🔴 / 備考)
- "変更履歴" 節に 1 行追記 (日付 + 要約)
- 先頭の "最終更新" 行を更新

commit は論理単位で分割 (scripts/VERSIONS 更新 と compat module 追加は別 commit が
レビューしやすい)。`chapter-test-parallelization` 進行中の時は作業ツリーに残さない。

## 参考

- 過去実例と詳細な背景: `.local/multi-kotlin-versions/current.md`
  (アーキテクチャ、API 境界、変更履歴)
- kctfork リリース: https://github.com/ZacSweers/kotlin-compile-testing/releases
- Compose Multiplatform リリース: https://github.com/JetBrains/compose-multiplatform/releases
- Kotlin リリース: https://github.com/JetBrains/kotlin/releases
- 既存 compat module: `debuggable-compiler-compat-k23` (最新 / 2.2.0+)、
  `debuggable-compiler-compat-k2000` (最古 / 2.0.0-2.0.10)
- ServiceLoader dispatch の仕組み:
  `debuggable-compiler-compat/src/main/kotlin/me/tbsten/debuggable/compiler/compat/IrInjectorLoader.kt`
  — `minVersion` が `≤ 現 compiler` のうち最大の factory を選ぶ
