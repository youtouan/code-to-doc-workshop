> ワークショップ用の固定の本文です。3-2 の講師デモで、cloud agent に渡す小さな Issue として使います。ISSUE-001 とは独立していて、同じファイルを変えません。
> `gh issue create --title "ISSUE-002 受注グループの行数の上限ちょうど（200行）を受け付けるテストを足す" --body-file fixtures/issues/ISSUE-002.md`

## 背景

受注 CSV の1つのグループ（同じ `external_key` が続く行）の行数の上限は `OrderGroup.MAX_LINES`（200）です。上限を超えると、グループ全体を `orderImport.groupLimit` で却下します（`wholesale-batch/src/main/java/jp/co/tsubame/wholesale/batch/OrderGroup.java` の17行目、49〜50行目）。

201行のグループを却下することは、`OrderCsvTest` の `moreThanTwoHundredLinesRejectsOneGroupWithoutSplitting` が確かめています。一方、ちょうど200行のグループを受け付けることを確かめるテストはありません。

## やること

`wholesale-batch/src/test/java/jp/co/tsubame/wholesale/batch/OrderCsvTest.java` に、テストを1つ足します。

- 商品コードがすべて異なる200行のグループを読み、`validate()` が例外を投げないこと
- そのグループの `getRecords()` が200件を返し、`getLastRow()` が200であること

## 守ること

- AGENTS.md に従う。挙動の変更を認めた ADR はないので、外から見える挙動を変えない。
- 本番のコード（`src/main`）を変えない。
- 既存のテストと同じく JUnit 4 で書き、Java 7 の文法を使う（`maven.compiler.source` と `target` は 1.7。ラムダ式を使わない）。

## 完了条件

- [ ] 足したテストが緑
- [ ] 既存のテストが緑のまま
- [ ] 変えたファイルは `OrderCsvTest.java` だけ

確かめるコマンド：

```bash
mvn -B -ntp -pl wholesale-batch -am test -Dtest=OrderCsvTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -ntp verify
```

## 範囲外

- `OrderGroup.java` などの本番のコードと、上限の値
- ISSUE-001 の範囲（重複した商品の却下理由、ACC-001、CHAR-ORD-001）
- As-Is 文書と ADR
