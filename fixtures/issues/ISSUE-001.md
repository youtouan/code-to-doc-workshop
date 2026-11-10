> ワークショップ用の固定の本文です。2-3 で次のコマンドで Issue にします。
> `gh issue create --title "ISSUE-001 重複商品の却下理由に入力行番号を含める" --body-file fixtures/issues/ISSUE-001.md`

## 背景

受注 CSV の取り込みでは、同じ `external_key` のグループに同じ商品コードが2回以上現れると、グループ全体を `orderImport.duplicateProduct` で却下します（採否表の F-003、観察 OBS-001）。却下理由のメッセージには商品コードしか入らないため、取引先は CSV のどの行を直せばよいかがわかりません（教材の想定）。

## 決定

- 決定の正本は ADR-0001（`docs/adr/0001-order-import-validation.md`）です。この Issue には決定の内容を写しません。
- ADR-0001 の Status が `accepted` になってから着手します。
- 期待する結果は、ADR-0001 から作った受け入れテスト ACC-001 に書いてあります。

## 範囲

- `wholesale-batch` の受注 CSV の取り込みで、重複した商品の却下理由のメッセージを、ADR-0001 の決定どおりにする。
- 変えたコードを As-Is 文書（`docs/as-is/`）が引用していれば、同じ PR で引用と主張を直す。直すかどうかは drift 検出の結果で判断する。

## 守ること

- AGENTS.md と ADR-0001 に従う。
- エラーコード `orderImport.duplicateProduct` を変えない。
- 特性テスト CHAR-ORD-001（`tests/characterization/`）を書き換えない。
- ACC-001（`wholesale-batch/src/test/java/jp/co/tsubame/wholesale/batch/OrderDuplicateAcceptanceTest.java`）の期待値を書き換えない。ACC-001 を変えてよいのは、ADR-0001 を改訂したときだけ。
- 範囲外のファイルを変えない。

## 完了条件

- [ ] ACC-001 が緑になる（実装前は赤）
- [ ] CHAR-ORD-001 が緑のまま（ラボで実行）
- [ ] 既存のテストが緑のまま
- [ ] drift 検出を実行し、ずれた引用を直したか、直さない理由を PR に書いた
- [ ] 変えた As-Is 文書は、checker が pass
- [ ] PR の説明に、変えた振る舞いの一覧がある

確かめるコマンド：

```bash
mvn -B -ntp -pl wholesale-batch -am test -Dtest=OrderDuplicateAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false
node --test "tests/characterization/**/*.test.mjs"
mvn -B -ntp verify
node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/main --head HEAD
node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/<変えた文書>.md
```

drift 検出の `--base` には、As-Is 文書があるコミットを指定します（`docs/procedures/doc-drift-check.md` の手順1）。

## 範囲外

- 却下の単位を変えること（重複した行の数量の合算、2行目以降だけの却下など）
- ほかの却下（`orderImport.headerConflict`、`orderImport.quantity`、`order.pack` など）のメッセージ
- 行ごとの結果（row-results）の列の形式と、`describeSource()` の表記
- 画面（`wholesale-web`）と、データベースの定義（`database/`）
- テストの仕組みの変更（JUnit 5 への移行など）
- ADR-0001 の改訂

## 参考

- 根拠：F-003（`docs/as-is/findings/order-import.md`）、OBS-001（`docs/as-is/observations.md`）、CHAR-ORD-001（`tests/characterization/order-import.characterization.test.mjs`）
- 計画を Issue に添付する場合のひな形：`docs/templates/issue-plan-artifact.md`
- チームが ADR-0001 で教材の想定と違う決定をした場合は、ACC-001 をその決定に合わせて書いたうえで、この Issue の完了条件を読み替えてください。
