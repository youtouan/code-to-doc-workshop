# 手順書：根拠つきの分析（evidence-analysis）

skill `legacy-evidence-analysis` を使えない環境（Copilot Free など）で、同じ成果物を作るための手順書です。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

見慣れないレガシーコードを読み、根拠つきの主張を採否表（findings）にまとめる。どの主張を採用するかは人が決める。

## 入力

足りない項目は、推測せずに依頼者に聞く。

- 範囲（機能、モジュール、バッチ、処理の流れ）と、範囲外のもの
- 読む人と、本文の言語（指定がなければ日本語）
- 許された操作：既定は読み取りだけ。アプリやテストを動かすのは、依頼で許されたときだけ
- 根拠を読んだ時点のコミット：`git rev-parse HEAD` の値（40桁）

## 手順

1. 入口（URL、アクション、バッチのコマンド、スケジュール）から読み始める。呼び出し元、共通のチェック、呼び出し先、設定、永続化を、範囲に必要なところまで追う。読む観点は `.github/skills/legacy-evidence-analysis/references/reading-checklist.md` から、範囲に必要なものだけを選ぶ。
2. 主な主張には、次の形式で根拠を付ける。
   - コード：`[[source:path/from/repo/root#L12-L18]]`（1行だけなら `#L42`）
   - 動かした結果：`[[runtime:OBS-001]]`（`docs/as-is/observations.md` に記録済みの ID）
   - 実行したテスト：`[[test:path/to/test-file#CHAR-ORD-001]]`
3. 事実（根拠がある）、推測（理由を書く）、Unknown（次に何を読むか、何を観察するかを書く）を分ける。要件、経緯、意図を作らない。
4. `.github/skills/legacy-evidence-analysis/assets/templates/findings.md` を `docs/as-is/findings/<範囲>.md` に写して、次のように埋める。
   - メタデータ：`scope` に範囲、`sourceCommit` に入力のコミット。見出しの `{{TITLE}}` に題名
   - 行の ID：`F-001`、`F-002`、…
   - Type：`fact`、`inference`、`unknown` のどれか。`unknown` の行は、Claim に「何がわからないか」と「次に読むもの、または観察」を書き、Evidence には手がかりの引用か `—` を書く
   - Decision：すべて `pending`。Reason と Decided by は空のまま
5. チャットで次の3点を返す。
   - 追加した行の一覧
   - Unknown ごとの、次に読むファイルか、次に行う観察
   - 依存関係とデータの流れの Mermaid（「下書き」と明記する）

## 出力

- `docs/as-is/findings/<範囲>.md`（例：`docs/as-is/findings/order-import.md`）
- チャットの返答（手順5の3点）

## ルール

- 根拠にしてよいのは、アプリのコード、設定、SQL、サンプルデータ、記録済みの観察、実行したテスト。`.github/`、`.runtime/`、`docs/as-is/`、`AGENTS.md`、ワークショップの成果物、それまでのチャットの回答は根拠にしない。
- パスはリポジトリのルートからの相対パスで書き、区切りは `/` にする。1つの引用は200行まで。
- 実行していないテストは、意図した確認を示すだけで、実際の挙動の根拠にならない。
- 別のモデルやエージェントが同意しても、それはレビューの材料で、証明ではない。
- Decision を `adopted` や `rejected` にしない。Reason と Decided by を書かない。
- この手順では、As-Is 文書、テスト、コードを書かない。

## 止める条件

- 行は合わせて6件まで。そのうち Type が `unknown` の行は2件まで。依頼で別の上限を決めた場合は、それに従う。
- 範囲を読み終えるか、次に必要なもの（読めないファイル、許されていない操作）がはっきりしたら止める。

## 確かめ方

findings は checker（`check-evidence.mjs`）の対象外です。引用が主張と合っているかは、人が引用先を開いて確かめます（待っている間のカード W1）。

人が決める欄を書いていないことは、次のコマンドで確かめます。終了コードは 0 が変更なし、2 が変更あり、3 が判定不能です。

```bash
node .github/skills/as-is-documentation/scripts/check-human-fields.mjs --base HEAD --head-dir .
```
