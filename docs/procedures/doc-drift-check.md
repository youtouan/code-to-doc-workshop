# 手順書：根拠のずれの検出（doc-drift-check）

skill `doc-drift-check` を使えない環境（Copilot Free など）で、同じ検出と更新案を出すための手順書です。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

変更によって、As-Is 文書が引用しているコードの行が動いた、変わった、消えた主張を見つけ、文書の更新案を出す。文書を直すかどうかは人が決める。

## 入力

- 比べる2つの状態：基準（文書が引用を確かめた時点のコミット）と、変更後（コミットか作業中のファイル）
- 文書の場所：既定は `docs/as-is`（採否表 `docs/as-is/findings/` も含む）

## 手順

1. リポジトリのルートで検出器を実行する。基準には、文書がすでにあるコミットを選ぶ。
   - PR やブランチ（基準の文書が main にある場合）：`git fetch origin main` を実行してから、`node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/main --head HEAD`
   - 直前のコミットに文書があり、その後のコミットでコードが変わった場合：`node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base HEAD~1 --head HEAD`
   - まだ commit していない変更：`node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base HEAD --working-tree`
   - 機械で読む出力が要るときは `--json` を、文書が `docs/as-is` 以外にあるときは `--docs <フォルダー>` を足す。
2. 終了コードを読む。0 はずれなし、2 はずれあり、3 は判定不能（入力を直す。合格ではない）。
3. 0 のときは、基準のコミットに引用つきの文書があったかを `git ls-tree -r --name-only <基準> -- docs/as-is` で確かめる。検出器が調べるのは基準の側の文書にある引用だけなので、基準に文書がなければ、コードが動いていても 0 になる。その場合は、基準を選び直して報告する。
4. 2 のときは、次の順に1件ずつ扱う：`file-removed`、`content-changed`、`citation-changed`、`file-renamed`、`line-shift`。変更後のコードで引用先を読み直し、次のどれかを提案する。
   - 主張と行の範囲を直す（新しい文言と `[[source:...#Lstart-Lend]]`）
   - 主張を `## Unknowns` に移す（理由を書く）
   - 人に文書の読み直しを頼む
5. `line-shift` は行の範囲を直すだけで済むことが多い。ただし、引用先が今も同じことを書いているかを必ず確かめる。`citation-changed` は、変更で基準の側の引用が消えたか、付け替えられたことを示す。
6. 報告の表と更新案を、チャットで見せる。PR で使う場合は、表と更新案を1つのコメントにまとめる（コメントするのは人）。頼まれない限り、文書を変えたり push したりしない。

## 出力

- 検出器の報告（出力をそのまま）
- ずれごとの更新案。人が採った更新案は、文書を直す PR に入れる

## ずれの種類

- citation drift：引用したコードが動いた、消えた、付け替えられた。この検出器が扱うのは、これだけ。
- runtime／configuration drift：コードは同じでも、設定、データ、環境の違いで挙動が変わった。検出器では見つからない。ラボで特性テスト（`node --test "tests/characterization/**/*.test.mjs"`）と観察（`sh scripts/observe-import.sh samples/orders-rejections.csv`）をやり直して確かめる。

## ルール

- 文書に合わせて、引用先のコードを変えない。
- ずれを消すために、文書を `reviewed` にしたり、Unknown を消したりしない。
- frontmatter（`owner`、`sourceRevision`、`reviewedAt`、`reviewBy`）を書き換えない。更新案を採った人が、確かめ直したコミットと見直した日を書く。
- 人が更新案を採って文書を直したら、checker を通す。
- ずれがなくても、`reviewBy` の日を過ぎた文書は人が読み直す。checker はこれを `review-overdue` の警告で示す。

## 止める条件

- 報告のすべての行に、更新案か「人が読み直す」を付けたら止める。
- 判定不能（終了コード3）なら、理由を報告して止める。

## 確かめ方

```bash
node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/main --head HEAD
node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/<文書>.md
```

1行目は、手順1で選んだ基準に合わせて書き換えてください。
