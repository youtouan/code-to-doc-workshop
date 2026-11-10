# 手順書：As-Is 文書（as-is-documentation）

skill `as-is-documentation` を使えない環境（Copilot Free など）で、同じ成果物を作るための手順書です。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

採用した根拠だけを使って、As-Is 文書を Markdown と Mermaid で書く。書いたら checker で構造と引用先を確かめる。

## 入力

- 採否表で Decision が `adopted` の行（`docs/as-is/findings/*.md`）
- 文書の種類：`module-map`、`feature-spec`、`process-design`、`batch-runbook` のどれか
- 出力先：`docs/as-is/` の下の1ファイル（例：`docs/as-is/order-import-module-map.md`）
- 読む人と、本文の言語（指定がなければ日本語）

## 手順

1. 種類に合うテンプレートを `.github/skills/as-is-documentation/assets/templates/` から写す（`module-map.md`、`feature-spec.md`、`batch-runbook.md`）。`process-design` にはテンプレートがないので、ほかのテンプレートと同じ frontmatter とメタデータのコメントを置き、`.github/skills/as-is-documentation/references/artifact-contract.json` の `sections` の順に `##` 見出しを並べる。
2. 先頭の YAML frontmatter（`owner`、`sourceRevision`、`reviewedAt`、`reviewBy`）は、空のまま残す。値は、人が見直すときに書く。
3. frontmatter のすぐ後にあるメタデータのコメント（`<!-- code-to-doc-artifact`）では、`kind` と `scope` を埋める。`status` は `draft` のままにする。コメントにキーを足さない（知らないキーがあると、checker が invalid にする）。
4. `##` の見出しは、テンプレートの英語の見出しを1文字も変えず、同じ順に置く。本文は依頼の言語で書く。
5. テンプレートの例の行（`{{...}}` を含む行）は、すべて実際の内容に置き換えるか消す。残すと checker が invalid にする。
6. `adopted` の行だけを使う。`## Scope`、`## Evidence`、`## Unknowns` 以外の節では、箇条書きと表の各行に根拠を1つ以上付ける：`[[source:...]]`、`[[runtime:OBS-001]]`、`[[test:path#ID]]`、`[[unknown:U-1]]`。
7. 根拠のない点は `## Unknowns` に `- U-1: <わからないこと> (next read: <次に読むもの、または観察>)` の形で書く。状態のタグは ` [status: investigating]` だけを付けてよい。
8. 図は、`module-map` なら `## Dependencies` と `## Data flow` に、`process-design` なら `## Call sequence` に Mermaid で置く。図の下には、根拠つきの行を1行以上書く。
9. checker を実行し、出力をそのまま見せる。
10. 文書、checker の結果、残った Unknown を、人か `evidence-reviewer` に渡す。`evidence-reviewer` を使えない場合は、手順書 `docs/procedures/evidence-review.md` を使う。

## 出力

- `docs/as-is/<名前>.md`（`status` は `draft`）
- checker の結果（出力をそのまま）

## ルール

- 根拠の書き方と、根拠にしてよいものは `docs/procedures/evidence-analysis.md` と同じ。
- `[[runtime:OBS-001]]` が通るのは、`docs/as-is/observations.md` の表に `| OBS-001 |` の行が1つだけあり、その行に `{{...}}` が残っていないときだけ。
- `[[test:path#ID]]` は、そのファイルに ID の文字列があれば通る。実行したかどうかは checker にはわからないので、実行していないテストは引用しない。
- 箇条書きでも表でもない根拠のない文は、警告（`uncited-prose`）になる。主張は根拠つきの箇条書きにするか、Unknown に移す。
- 人が決める欄は書かない。frontmatter の値を書かない。`status` を `reviewed` にしない。Unknown に ` [status: resolved]` や ` [status: accepted]` を付けない。採否表の Decision、Reason、Decided by を書き換えない。

## 止める条件

- checker が `pass`（終了コード0）になったら止める。
- `invalid`（終了コード1）なら、指摘された行だけを直して、1回だけ実行し直す。2回目も `invalid` なら、指摘された主張を `## Unknowns` に移し、そのことを報告して止める。根拠や Unknown を消して通さない。`owner-missing`（`reviewed` の文書に `owner` がない）は人が直すので、報告だけする。
- `uncheckable`（終了コード3）は合格ではない。対象のパスや引数を直す。
- 警告（`uncited-prose`、`frontmatter-date`、`review-overdue`）は判定を変えない。警告が出たら、そのまま報告する。
- コマンドを実行できない場合は「未実行（not run）」と報告する。見ていない結果を報告しない。

## 確かめ方

```bash
node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/order-import-module-map.md
node .github/skills/as-is-documentation/scripts/check-human-fields.mjs --base HEAD --head-dir .
```

- `check-evidence.mjs`：0 が pass、1 が invalid、3 が uncheckable。pass が示すのは、frontmatter、メタデータ、構造、引用先が正しいことだけ。主張が正しいことや、漏れがないことは示さない。
- `check-human-fields.mjs`：0 が変更なし、2 が人の欄（frontmatter を含む）の変更あり、3 が判定不能。2 がエージェントの変更から出たら、その変更を元に戻す。
