# 追いつき用の patch

テンプレートのブランチでは、追いつき用の状態を配りません（テンプレートから作ったリポジトリでは、ブランチ同士の履歴がつながらず、PR を作れないため）。代わりに、復旧点ごとのコミットを patch として置きます。patch は `git format-patch --stdout` で作った mbox を gzip で圧縮したファイル（`catch-up/<復旧点>.patch.gz`）で、コミットとメッセージを1件ずつ持っています。

圧縮してあるのは、このリポジトリで作業するエージェントが、検索やファイルの読み取りのついでに、用意済みの状態（演習の答え）を読まないようにするためです。展開すれば読めるので、アクセス制御ではありません。

`scripts/catch-up.sh`（Windows の手元では `scripts/catch-up.ps1`）は、参加者のリポジトリの `main` の最初のコミット（テンプレートから作った状態）からブランチ `catch-up/<復旧点>` を作り、patch のコミットを `git am` で順に当てます。

## 使い方

```bash
sh scripts/catch-up.sh 2-1             # Windows の手元では pwsh scripts/catch-up.ps1 2-1
sh scripts/catch-up.sh 2-1 --dry-run   # 当てられるかと、作られるコミットの数だけを確かめる
```

- 未 commit の変更（追跡していないファイルを含む）があると止まります。先に commit するか、`git stash -u` で退避します。
- 同じ名前のブランチ `catch-up/<復旧点>` がすでにあると止まります。
- 途中で当てられなかったときは、`git am --abort` を実行し、元のブランチに戻って、作りかけのブランチを消します。
- `--dry-run` は、`.git` の中に一時的な worktree（基点のコミット、detached）を作り、`git am` を最後まで試してから消します。今のブランチもファイルも変えず、ブランチも残しません。成功すると `dry-run ok:` で始まる行と、作られるコミットの件名を表示します。
- git の `user.email` が設定されていないときだけ、コミットした人（committer）を `catch-up@example.invalid` にします。作者（author）とメッセージは patch のとおりです。
- 終了コード：0 は成功、1 は当てられない・未 commit の変更がある、3 は使い方の誤り・patch がない、です。
- `main` の最初のコミットに `catch-up/<復旧点>.patch.gz` がないと、終了コード1で止まります（`--dry-run` も同じ）。そのため、テンプレートのリポジトリそのものや fork では使えません。「Use this template」で作ったリポジトリで実行します。
- 浅い clone（`git clone --depth`）でも、最初のコミットがわからないので止まります。`git fetch --unshallow origin` を実行してから使います。
- `scripts/catch-up.sh` は `gzip` で patch を展開します。ラボ、Git Bash、macOS には入っています。`scripts/catch-up.ps1` は .NET の機能で展開するので、`gzip` は要りません。

## 復旧点と中身

patch は累積です。各復旧点の patch には、それより前の復旧点のコミットも入っています。

| 復旧点 | コミット | 中身 |
|---|---|---|
| `1-2` | 2 | 採否表 `docs/as-is/findings/order-import.md`（F-001〜F-006。F-003 は `OrderGroup.java#L81-L83` を引用）と、As-Is 文書 `docs/as-is/order-import-module-map.md`。1つ目はエージェントの下書き（Decision は `pending`）、2つ目は採否と As-Is 文書 |
| `1-4` | 5 | 1-2 に加えて、観察ログ `docs/as-is/observations.md` の OBS-001（講師の試走で観察した値）、As-Is 文書の `[[runtime:OBS-001]]`、reviewer の指摘を書いた F-004 の Reason |
| `2-1` | 7 | 1-4 に加えて、`tests/characterization/order-import.characterization.test.mjs` の CHAR-ORD-001 と、As-Is 文書の `[[test:...#CHAR-ORD-001]]` |
| `2-2` | 10 | 2-1 に加えて、ADR-0001 `docs/adr/0001-order-import-validation.md`（草案のコミットと、決定を書いたコミット。Status は `accepted`）と、期待値を書いて `@Ignore` を外した ACC-001（実装前なので赤） |
| `2-4` | 11 | 2-2 に加えて、AGENTS.md のルール7（ADR-0001 に沿って、エラーコードとメッセージの先頭を変えない、CHAR-ORD-001 を書き換えない）。ISSUE-001 は GitHub の Issue なので入らない |
| `3-1` | 12 | 2-4 に加えて、ISSUE-001 の実装（`OrderGroup.java`）と、同じコミットでの F-003 の引用の直し（`#L81-L83` から `#L83-L86`）。ACC-001 は緑、CHAR-ORD-001 も緑のまま |
| `eval-no-doc` | 1 | 2-4 の A/B eval の A（Doc なし）。2-2 と同じコードとテストから、下の「eval-no-doc で除いたもの」を除いた状態 |
| `drift-fixture` | 2 | 4-1 の固定の drift。下の「drift-fixture」を参照 |

人が書く欄は、講師が追いつき用の例として書きました（Decided by は「講師（追いつき用の例）」）。対象は、採否表の Decision・Reason・Decided by、ADR-0001 の決定・理由・署名と Status、As-Is 文書と ADR の frontmatter（`owner`・`sourceRevision`・`reviewedAt`・`reviewBy`）です。ADR-0001 の決定は、配布物「ACC-001：To-Be 受け入れテストの仕様」の想定の決定と同じです。自分たちの判断と違う場合は、書き直してから続けます。3-1 の frontmatter は、2-4 のままです（実装の PR を確かめた人が更新する欄のため）。

`sourceRevision` には、テンプレートの基点 `b5b25103b087a6cdde04ca02aeab5c0ddcffe481` を書きました。参加者のリポジトリの履歴にこのコミットはありませんが、コードは `main` の最初のコミットと同じです。

`1-2` で採否を自分たちで書くときは、`catch-up/1-2` で `git reset --hard HEAD~1` を実行すると、1つ目のコミット（Decision が `pending` の下書き）に戻ります。As-Is 文書も消えるので、1-2 の手順に沿って作り直します。

## ラボで使うとき

追いついたブランチを手元で push し、ラボでも同じブランチに切り替えます。

`sh scripts/observe-import.sh`（特性テストもこれを使います）は、実行のたびに、今のコードからバッチのイメージを作り直してから動かします。コードが変わっていなければ Docker のキャッシュが使われ、数秒で終わります。`3-1` と `drift-fixture` は `OrderGroup.java` を変えるので、切り替えた後の最初の実行では Maven のビルドに数分かかります。ビルドの記録は `.runtime/observe/<データベース名>/build.log` に残ります。

文書やテストを変えても、イメージは作り直しません。`.dockerignore` で、イメージのビルドに使うファイルを Maven のプロジェクト（`pom.xml` と3つのモジュール）と Tomcat の設定（`runtime/`）に限っているためです。Web アプリ（`web`）は `OrderGroup.java` を使わないので、追いついた後も作り直す必要はありません。

## eval-no-doc で除いたもの

A と B の違いを Doc だけにするため、2-2 と同じコードとテスト（CHAR-ORD-001 と、期待値を書いた ACC-001）を1つのコミットにまとめ、次を除きました。履歴にも Doc は残りません。

- `AGENTS.md`
- `.github/agents/`（`evidence-reviewer`、`legacy-analyst`）
- `.github/hooks/`（`code-to-doc.json`）
- `.github/prompts/`（`analyze-feature.prompt.md`）
- `.github/skills/`（Code to Doc の skill 一式）
- `docs/`（`docs/procedures/`、`docs/templates/`、`docs/as-is/`、`docs/adr/`）

`.github/copilot-instructions.md` と `.github/instructions/` は、テンプレートにないので除くものはありません。コードとテストのほかに残る文章は、`README.md`、`fixtures/`（ISSUE-001 の本文を含む）、`.github/workflows/`（`doc-drift-guard.md` を含む）、`.github/code-to-doc-kit.export.json`、`catch-up/` です。`catch-up/` の patch には Doc の本文が入っています。gzip で圧縮してあるので、エージェントの検索やファイルの読み取りには出てきませんが、展開すれば読めます。A を実行した後は、エージェントが `catch-up/` の patch を展開していないことを、セッションの記録で確かめます。

## drift-fixture

4-1 で全員が使う、固定の drift です。3-1 の成果には依存しません。2つのコミットでできています。

1. 第2章の終わり（`2-4`）と同じ文書（採否表、As-Is 文書、OBS-001、ADR-0001、AGENTS.md のルール7）と CHAR-ORD-001。ACC-001 は骨格のままにして、ビルドを緑に保ちます。
2. `OrderGroup.validate()` からヘッダーの検査を `requireSameHeader()` に切り出した、挙動を変えない変更。F-003 が引用している重複チェックの行が別の位置に動きます。

比べるときは、直前のコミット（文書を置いたコミット）と最新のコミットを指定します。

```bash
node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base HEAD~1 --head HEAD
```

F-003 の引用4か所（採否表に1か所、As-Is 文書に3か所）が `line-shift` として報告され、終了コードは2になります。`--base origin/main` で比べると、基準の側に文書がないため、ずれは見つかりません（終了コード0）。

## patch の作り直し方

基点は、テンプレートの `main` で追いつきの仕組みを入れる前のコミット `b5b25103b087a6cdde04ca02aeab5c0ddcffe481` です。テンプレートから作ったリポジトリの最初のコミットは、次のファイルを除いて、この基点と同じ中身になります。patch では、これらのファイルを変えません。

- `catch-up/`、`scripts/catch-up.sh`、`scripts/catch-up.ps1`（追いつきの仕組み）
- `scripts/observe-import.sh`（実行のたびにバッチのイメージを作り直す）、`.dockerignore`（イメージのビルドに使うファイルを限る）、`compose.yml`（データベースの準備ができたことを TCP の接続で確かめる）
- `.gitattributes`（`catch-up/*.patch.gz` を binary として扱う）

テンプレートの `main` で、patch が変更・削除するファイルを変えたときは、patch を作り直します。`eval-no-doc` は `.github/` の skill なども削除するため、kit を更新したときも作り直しが必要です。

```bash
base=b5b25103b087a6cdde04ca02aeab5c0ddcffe481
repo=$PWD
# 別の worktree で、今の状態をブランチに戻す（1-2 → 1-4 → 2-1 → 2-2 → 2-4 → 3-1 は、前の復旧点の上に積む）
git worktree add -b st/2-2 ../c2d-states "$base"
cd ../c2d-states
gzip -dc "$repo/catch-up/2-2.patch.gz" | git am
# 直して commit する。前の復旧点を直したら、後ろの復旧点も同じ順に積み直す
git format-patch --stdout --no-signature --zero-commit "$base..st/2-2" | gzip -9 -n > "$repo/catch-up/2-2.patch.gz"
```

Windows では Git Bash で実行します。PowerShell の `>` と `|` は、文字コードや改行を変えることがあるためです。`gzip -n` は元のファイル名と時刻を書き込まないので、同じ patch からは同じファイルができます。

- `eval-no-doc`：基点からブランチを作り、`st/2-2` から `tests/characterization/order-import.characterization.test.mjs` と `wholesale-batch/src/test/java/jp/co/tsubame/wholesale/batch/OrderDuplicateAcceptanceTest.java` を取り出し、上の Doc を `git rm -r` で消して、1つのコミットにします。
- `drift-fixture`：基点からブランチを作り、`st/2-4` の文書と CHAR-ORD-001 を1つ目のコミット、`OrderGroup.java` の挙動を変えない変更を2つ目のコミットにします。
- 作り直したら、テンプレートから作ったリポジトリで、全部の復旧点の `--dry-run` を確かめます。