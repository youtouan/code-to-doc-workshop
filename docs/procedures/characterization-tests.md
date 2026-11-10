# 手順書：特性テスト（characterization-tests）

skill `characterization-tests` を使えない環境（Copilot Free など）で、同じテストを作るための手順書です。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

レガシーシステムの今の挙動を、作り直しても残る境界でテストに固定する。おかしく見える挙動も、そのまま固定する。

## 入力

- 固定する挙動と、その根拠（As-Is 文書の主張、採否表の行、`docs/as-is/observations.md` の観察）
- 境界：受注 CSV の取り込みなら、入力の CSV と、グループごとの結果（エラーコード）、作られた受注、プロセスの終了コード
- レガシーシステムの起動とデータの戻し方：テンプレートの土台に用意してある（次の節）

## 土台（テンプレートに用意済み）

- `tests/characterization/order-import.characterization.test.mjs`：受注の取り込みのテスト。`before` で `samples/orders-rejections.csv` を1回だけ取り込み、各テストはその結果を読む。例として `CHAR-ORD-002`（得意先が食い違うグループの却下）がある。
- `tests/characterization/support/order-import.mjs`：`importOrders(csv)` が `scripts/observe-import.sh --json` を呼ぶ。専用のデータベースを `database/*.sql` から作り直してから、バッチの `import-orders` を1回実行し、結果を返す。アプリのデータベースには触れない。
- 結果の読み方
  - `rowFor(result, 'CSV-ORDER-HEADER')`：そのグループの結果。`result`（`SUCCESS` か `REJECTED`）、`code`、`message`、`sourceRows`（例：`3..4`）
  - `ordersCreatedFor(result, 'CSV-ORDER-HEADER')`：その `external_key` で作られた受注の数
  - `result.exitCode`：バッチのプロセスの終了コード

## 手順

1. いちばん外側の境界を選ぶ（HTTP、画面のメッセージ、バッチの入出力と終了コード、データベースの行）。内部のクラス（例：`OrderGroup`）を直接呼ぶテストは、作り直すと使えなくなるので書かない。
2. 期待値は、レガシーシステムが今返す値から書く。確かめるときは、ラボで `sh scripts/observe-import.sh samples/orders-rejections.csv` を実行するか、OBS-001 を読む。
3. 土台のファイルの `test.todo(...)` の行を、`test('<ID> <固定する挙動>', ...)` に置き換える。テスト名は依頼された ID（例：`CHAR-ORD-001`）で始める。1つのテストで固定するルールは1つにする。
4. 依頼で固定しないと決めたもの（例：メッセージの文言）は比べない。
5. 今の挙動がおかしく見えても、期待値は変えない。疑問はテストのコメントに書き、Unknown か不具合の候補として記録する。
6. 毎回変わる値（時刻、採番された ID、順序の保証がない並び）だけを正規化し、正規化したことをテストに書く。
7. ラボで実行し、実行したコマンドと結果をそのまま報告する。

## 出力

- `tests/characterization/` のテスト（名前は ID で始める）
- 文書から引用するときの書き方：`[[test:tests/characterization/order-import.characterization.test.mjs#CHAR-ORD-001]]`

## ルール

- テストを通すために、レガシーのコードを変えない。
- 期待値を弱めない。テストを飛ばしたり消したりしない。変えてよいのは、承認された ADR がそのテストを名指ししたときだけ。
- 共有のデータや本番のデータに向けない。土台は、専用のデータベースを毎回作り直す。
- 実行していないテストは根拠にならない。
- テストを足す前のファイルに、まだ書いていないテストの ID を書かない。文書がその ID を引用すると、checker が通ってしまう。

## 止める条件

- テストを足し、ラボで緑になったら止める。
- 手元（DESK）で Docker を使えない場合は、テストを書いたところで止め、「未実行」と報告する。実行はラボで行う。
- 時間切れ（2-1 では8分）は人が宣言する。宣言されたら、そこまでの状態を報告して止める。

## 確かめ方（ラボで、リポジトリのルートから）

```bash
node --test "tests/characterization/**/*.test.mjs"
```

1回の実行で取り込みを1回行うので、およそ10〜20秒かかります（マシンが混んでいると1分ほど）。`node --test tests/characterization/` のようにフォルダーを渡すと、Node.js 22 はフォルダーをファイルとして読もうとして失敗します。上のように、引用符で囲んだパターンを渡してください。
