---
name: bump-library-version
description: >
  このリポジトリ (debuggable-compiler-plugin) の debuggable ライブラリバージョンを
  上げる。canonical source は root の `gradle.properties` の `debuggable.version=X.Y.Z`。
  これと同期が必要な約 10 ファイル (各 integration-test の gradle.properties +
  libs.versions.toml + 3 つの README + root README の install snippet) を一括で書き換え、
  最後に `chore(release): bump to X.Y.Z` で auto-commit する。単純な「next patch
  version」でもよいし、明示的に「0.2.0 にして」「1.0.0-rc.1 にしたい」も受け付ける。
  このリポジトリで「バージョン上げて」「バージョンアップ」「N.N.N にして」「bump to
  X.Y.Z」「次の patch」「minor bump」「リリース準備」等を言われたら必ずこの skill を
  発動。手で sed すると README 内 stale バージョンを取り残すので使わないこと。
---

# Bump Library Version

このリポジトリの debuggable ライブラリ (`me.tbsten.debuggablecompilerplugin:*`) の
バージョンを一気に bump するための skill。

## なぜこの skill があるか

バージョン番号は **10 ファイル以上** に散らばっている:

| ファイル | 役割 |
|---|---|
| `gradle.properties` | **canonical** (`debuggable.version=X.Y.Z`)。すべての分岐点 |
| `integration-test/cmp/gradle.properties` | サンプルビルド時のデフォルト |
| `integration-test/android/gradle.properties` | 同上 |
| `integration-test/kmp-smoke/gradle.properties` | 同上 |
| `integration-test/cmp/gradle/libs.versions.toml` | `debuggable = "…"` — `libs.debuggable.runtime` の依存解決 |
| `integration-test/android/gradle/libs.versions.toml` | 同上 |
| `README.md` | Installation セクションの plugin/runtime 依存スニペット |
| `README.ja.md` | 同上 (ja) |
| `integration-test/README.md` | 依存座標の例示 |
| `integration-test/cmp/README.md` | 同上 |
| `integration-test/android/README.md` | 同上 |

手で `sed -i '' 's/0.1.N/0.1.N+1/g'` だと **以前の bump で取り残された stale 値**
(README 内にまだ 0.1.0 が残っている等) を拾えない。この skill は whitelist 方式で
**各ファイルの現在値を個別に読んで差し替える** ため、stale も直る。

## 触らないもの (明示的に除外)

- `integration-test/android/app/build.gradle.kts` の `versionName = "X.Y.Z"` — Android
  アプリ自体のバージョン、debuggable ライブラリとは別概念
- サードパーティライブラリのバージョン番号 (coroutines, compose, kotlin stdlib 等)
- `.local/**` — ticket/notes はリポジトリ公開物ではない
- `gradle/libs.versions.toml` (root) — `debuggable` 自身の catalog エントリは無い
  (compat-embeddable-kXX 等は Kotlin バージョンであって debuggable バージョンではない)

## 使い方

ユーザが「0.1.6 にあげて」「次の patch bump」「1.0.0-beta.1 にしたい」等を言ったら
このフローを **必ず** 実行する。

### Step 1. 現在値を読む

```
cat gradle.properties | grep '^debuggable.version='
```

`debuggable.version=X.Y.Z` の値が **canonical current version**。

### Step 2. target version を決める

ユーザの発話から target version を抽出する:

- 「0.1.6 にあげて」「`0.1.6`」→ `0.1.6`
- 「次の patch」「patch bump」→ current の patch を +1 (`0.1.5` → `0.1.6`)
- 「minor bump」「次の minor」→ `0.1.5` → `0.2.0`
- 「major bump」「1.0 にあげて」→ `0.1.5` → `1.0.0`
- 「beta / rc / alpha にして」→ pre-release suffix を付ける。形式が複数あり得る
  (`1.0.0-beta1`, `1.0.0-beta.1`, `1.0.0-RC1`) ので判別つかなければユーザに確認

target が曖昧なら推測せず **必ず聞く**。例:「patch bump で 0.1.6 でいい?」

### Step 3. 変更プランを提示 (dry-run)

以下のテーブルを **そのまま** 出力してユーザに見せる。各ファイルの **現在値** を
個別に grep して取得し、ズレがあれば (stale) を付ける:

```
  現在: 0.1.5  →  次:  0.1.6

  gradle.properties                                    0.1.5 → 0.1.6
  integration-test/cmp/gradle.properties               0.1.5 → 0.1.6
  integration-test/android/gradle.properties           0.1.5 → 0.1.6
  integration-test/kmp-smoke/gradle.properties         0.1.5 → 0.1.6
  integration-test/cmp/gradle/libs.versions.toml       0.1.5 → 0.1.6
  integration-test/android/gradle/libs.versions.toml   0.1.5 → 0.1.6
  README.md                                            0.1.5 → 0.1.6   (5 occurrences)
  README.ja.md                                         0.1.5 → 0.1.6   (5 occurrences)
  integration-test/README.md                           0.1.5 → 0.1.6   (6 occurrences)
  integration-test/cmp/README.md                       0.1.5 → 0.1.6   (2 occurrences)
  integration-test/android/README.md                   0.1.5 → 0.1.6   (1 occurrence)
```

stale があった場合の表示例:

```
  integration-test/README.md   0.1.0 → 0.1.6   (stale)   (6 occurrences)
```

一応ユーザの明示確認は **省略して OK**。「対象は あらかじめ決めておいてそこだけみる」
方針で、プレビューはあくまで情報提供。いきなり apply に進んで良い (ユーザが止めたい
ときは ctrl+c される)。

### Step 4. apply

各ファイルを Edit ツールで書き換える。sed ではなく Edit を使い、**各ファイルの
`old → new` は個別に計算** する (= 各ファイルの current 値を読んでから該当ファイル内
で置換)。これにより stale 値も一律 target に揃う。

置換ルール (file-type 別):

| File | Pattern | 置換ルール |
|---|---|---|
| `*/gradle.properties` | `debuggable.version=<old>` | `<old>` を target に |
| `*/libs.versions.toml` | `debuggable = "<old>"` | `<old>` を target に |
| `**/*.md` (whitelist 内) | `0.X.Y` / `X.Y.Z-foo` / `version "X.Y.Z"` / `:X.Y.Z` | ファイル内に出現する "自分たちのバージョンらしい" 値を全部 target に |

実装上は以下の sed で簡単に済む場合が多い (ただし sed は file ごとに current を
変える必要があるので、一括 sed はしない):

```bash
# ファイルごとにその file の current 値を取得してから replace
for f in <each whitelist file>; do
    current=$(grep -oE '<file-specific pattern>' "$f" | head -1 | ...)
    sed -i '' "s/$current/$target/g" "$f"
done
```

実用的には **各 file に対して Edit ツールの replace_all=true で old→new を個別適用**
するのが安全で読みやすい。

### Step 5. sanity check

refactor 後、各 integration-test が gradle.properties を参照する仕組みが
壊れていないことを軽く確認:

```bash
cd integration-test/cmp && ./gradlew help --no-daemon 2>&1 | tail -3
cd integration-test/kmp-smoke && ./gradlew help --no-daemon 2>&1 | tail -3
```

"BUILD SUCCESSFUL" を確認。失敗したら何が起きたかユーザに報告して止まる
(auto-commit しない)。

### Step 6. auto-commit

変更を stage し、以下の commit メッセージで commit する:

```
chore(release): bump to X.Y.Z

Synced version across:
- gradle.properties (canonical)
- integration-test/{cmp,android,kmp-smoke}/gradle.properties
- integration-test/{cmp,android}/gradle/libs.versions.toml
- README.md, README.ja.md, integration-test/**/README.md
EOF
```

(1 行目は short title。2 行目空行。以降 bullet body。`Co-Authored-By: Claude ...`
は付けない — リリース bump は機能変更ではないのでクレジット不要。)

実装:

```bash
git add -u && git commit -m "$(cat <<'EOF'
chore(release): bump to X.Y.Z

Synced version across gradle.properties, integration-test catalogs, and
README install snippets.
EOF
)"
```

**push はしない**。ユーザが手動で `git push origin main` する想定
(CI 通過確認 → release tag 作成 → Publish workflow という flow)。
push が必要な場合はユーザから明示指示をもらう。

## FAQ / 落とし穴

### Q: target が current と同じだったら?
skip。「現在すでに X.Y.Z」と報告して終わる。

### Q: target が current より古かったら?
危険 (Maven Central は immutable — 既に release 済みの version に戻せない) なので
ユーザに警告して明示確認を取る。

### Q: 事前に書き換えたいファイルが増えた場合
本 SKILL.md の whitelist テーブルを更新。新たに version を持つ場所が生まれたら
まずこの skill を更新。

### Q: libs.versions.toml が `debuggable-runtime = { module = "...:debuggable-runtime", version.ref = "debuggable" }` 形式だが `version.ref` は変更不要?
不要。`[versions]` の `debuggable = "X.Y.Z"` を置換するだけで OK。

### Q: 「リリースして」と言われたら?
この skill で bump commit までやる。その先 (push → tag → Publish workflow dispatch)
はユーザの判断なので、commit 完了時に「push + GitHub Release で 0.X.Y を切ると
Publish workflow が走ります」と案内だけして止まる。
