<!--
ISSUE-001 に issue artifact（type: plan）として添付する実装計画のひな形です。
このファイルは写して使い、{{...}} を埋めてから添付します。埋めた計画はリポジトリに commit しません。
決定の正本はリポジトリの ADR-0001 です。ここには要約とリンクだけを書き、ADR の本文は写しません。
-->
# ISSUE-001 の実装計画

- Issue：ISSUE-001（#{{Issue の番号}}）
- 決定の正本：[ADR-0001](https://github.com/{{OWNER}}/{{REPOSITORY}}/blob/main/docs/adr/0001-order-import-validation.md)（`docs/adr/0001-order-import-validation.md`）。Status：`accepted`。読んだコミット：`{{コミットの SHA}}`
- 書いた人と日付：{{名前}}、{{YYYY-MM-DD}}

## ADR-0001 の要約

この要約は作業のためのメモです。決定、理由、署名は ADR-0001 を読んでください。この計画と ADR-0001 が食い違ったら、ADR-0001 に従います。

- 決めたこと：{{1〜2行。例：重複した商品の却下理由に、その商品があるすべての入力行の番号を含める}}
- 変えないこと：{{例：グループ全体を却下すること、エラーコード `orderImport.duplicateProduct`}}

## 手順

1. {{変えるファイルと、変える内容}}
2. {{次の手順}}
3. ACC-001 と CHAR-ORD-001 を実行する（下の「確かめ方」）
4. drift 検出を実行し、ずれた引用があれば As-Is 文書の主張と引用を直す
5. PR の説明に、変えた振る舞いの一覧を書く

## 受け入れ

- ACC-001（`wholesale-batch/src/test/java/jp/co/tsubame/wholesale/batch/OrderDuplicateAcceptanceTest.java`）：実装前は赤、実装後は緑
- CHAR-ORD-001（`tests/characterization/order-import.characterization.test.mjs`）：緑のまま。書き換えない

## 範囲外

ISSUE-001 の「範囲外」をそのまま写します。

- {{範囲外の項目}}

## リスク

| リスク | 確かめ方・対策 |
|---|---|
| {{例：運用の手順や監視が、メッセージの先頭を照合している}} | {{例：先頭の `Product occurs more than once: <商品コード>` を変えない}} |
| {{例：引用している行が動き、As-Is 文書がずれる}} | {{例：drift 検出の結果を見て、同じ PR で引用を直す}} |

## 確かめ方

```bash
mvn -B -ntp -pl wholesale-batch -am test -Dtest=OrderDuplicateAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false
node --test "tests/characterization/**/*.test.mjs"
mvn -B -ntp verify
node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/main --head HEAD
node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/<変えた文書>.md
```

2行目の特性テストはラボで実行します。

## 添付のしかた

- GitHub の Issue の画面：ISSUE-001 の **Artifacts** で ＋ を押し、**Create artifact** を選ぶ。名前を付け、埋めた本文を貼る。
- GitHub CLI 2.103.0 以上：`gh issue artifact create {{Issue の番号}} --body-file {{埋めたファイル}} --type plan --name "ISSUE-001 plan"`（名前に括弧などは使えない）
- ADR-0001 が改訂されたら、この計画を作り直す。
