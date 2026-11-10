# 手順書：移行方針の ADR 草案（modernization-decision）

skill `modernization-decision` を使えない環境（Copilot Free など）で、同じ ADR 草案を作るための手順書です。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

1つの範囲について、選択肢を比べた ADR（Architecture Decision Record）の草案を作る。決定はしない。決定、理由、署名は人が書く。

## 入力

- 決める範囲（1つのルール、モジュール、処理の流れ、バッチ）
- その範囲の As-Is 文書、採否表、観察、特性テスト、未解決の Unknown
- 人が示した制約（期限、予算、技能、運用、コンプライアンス）。ワークショップでは制約カード（W3）
- 比べる選択肢。依頼で示されなければ、テンプレートの既定（Retain、Rehost、Replatform、Refactor、Rebuild）を使う。業務ルールの扱いを決める ADR では、依頼や制約カードにある選択肢を使い、今のままにする案（Retain）を必ず含める

## 手順

1. `.github/skills/modernization-decision/assets/templates/adr.md` を `docs/adr/NNNN-<slug>.md` に写す（例：`docs/adr/0001-order-import-validation.md`）。先頭の YAML frontmatter（`owner`、`sourceRevision`、`reviewedAt`、`reviewBy`）は、人が書くので空のまま残す。
2. 見出しの `# ADR-NNNN: <題名>` と、`- Status: proposed`、`- Scope:`、`- Related As-Is documents:` を埋める。
3. `## Context` に、判断にかかわる事実を根拠つきで書く（`[[source:...]]`、`[[runtime:OBS-001]]`、`[[test:...#CHAR-ORD-001]]`、採否表の ID）。
4. `## Options` の表は列をそのまま使い、行を比べる選択肢に置き換える。選択肢ごとに、利点、費用とリスク、影響する事実と Unknown（根拠つき）、外から見える挙動の変化を書く。
5. `## Characterization tests` に、緑のまま保つテストと、選択肢によっては変更が必要になるテスト（理由つき）を書く。
6. `## Decision`、`## Rationale`、`## Decided by / Date` は空のまま残す。`<!-- Human only ... -->` のコメントも残す。
7. 選択肢を隠したり、好みで順位を付けたりしない。推奨を求められたときだけ、前提を書いた別のメモで返す。

## 出力

`docs/adr/NNNN-<slug>.md`（`Status: proposed`）

## ルール

- Decision、Rationale、Decided by / Date を書かない。Status を `accepted` にしない。frontmatter も含めて、人の名前や日付を書かない。
- 挙動を変えてよいのは、人が ADR を `accepted` にした後だけ。
- 根拠の書き方は `docs/procedures/evidence-analysis.md` と同じ。根拠のない選択肢や事実を作らない。

## 止める条件

- 選択肢を2〜5個比べ、手順の欄をすべて埋めたら止める。
- 根拠が足りない点は、表の「影響する事実と Unknown」に Unknown として書き、推測で埋めない。

## 確かめ方

ADR は checker（`check-evidence.mjs`）の対象外です。人が決める欄を書いていないことを、次のコマンドで確かめます。終了コードは 0 が変更なし、2 が変更あり、3 が判定不能です。

```bash
node .github/skills/as-is-documentation/scripts/check-human-fields.mjs --base HEAD --head-dir .
```

エージェントの変更で 2 が出たら、エージェントが人の欄を書いています。その変更を元に戻してください。
