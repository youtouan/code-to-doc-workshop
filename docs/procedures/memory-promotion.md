# 手順書：Memory を Doc に昇格する（memory-promotion）

4-1 の追加課題「Memory を Doc に昇格する」の手順書です。Copilot Free でも行えます。チャットにこのファイルを添付し、「この手順に従って」と依頼します。

## 目的

memory の例ごとに引用をコードで確かめ、今のコードで正しい事実だけを、根拠つきで As-Is 文書に移す。古い事実と作業メモは、移さないという判断を記録する。

## 入力

- `fixtures/memories/memory-examples.jsonl`：memory の例3件（1行に1件。形式は `fixtures/memories/README.md` を参照）
- 今のブランチのコード
- 移す先：As-Is 文書（例：`docs/as-is/order-import-module-map.md`）。チームのルールや決まったコマンドなら `AGENTS.md`

## 手順

1. 各行の `externalId`、`fact`、`citations` を読む。
2. `citations` のうち `type` が `file` のものは、`path` の `lineStart` から `lineEnd` までを今のブランチで開き、`fact` と比べる。結果を次の3つに分ける。
   - 一致：引用先の行が `fact` を支えている
   - 不一致：引用先の行が `fact` と食い違う。または、行が動いて別の内容になっている
   - 作業メモ：コードの根拠がなく、その作業の間だけ意味のあるメモ
3. `.github/skills/legacy-evidence-analysis/assets/templates/findings.md` を `docs/as-is/findings/memory-promotion.md` に写し、memory 1件を1行にする。
   - ID：memory の `externalId`（例：`MEM-001`）
   - Claim：memory の `fact` と、手順2の結果（引用先に何が書いてあったか）
   - Evidence：確かめた行の `[[source:path#Lstart-Lend]]`。作業メモは、その memory の行（例：`[[source:fixtures/memories/memory-examples.jsonl#L3]]`）
   - Type：`fact`（手順2の結果は、確かめた事実なので）
   - Decision：`pending`。Reason と Decided by は空のまま
4. ここで止めて、人に採否を頼む。人は、Doc に移すものを `adopted`、移さないものを `rejected` にし、Reason に「古いので memory を削除する」「作業メモなので Doc にも memory にも入れない」などと書く。
5. `adopted` の行だけを、移す先の文書に書く。根拠は memory の引用を写すのではなく、今のコードで確かめた行を `[[source:...]]` で付ける。As-Is 文書なら、合う節の箇条書きか、`## Evidence` の表に1行足す。
6. checker を実行し、出力をそのまま見せる。PR は人が作る。

## 出力

- `docs/as-is/findings/memory-promotion.md`（Decision、Reason、Decided by は人が書く）
- `adopted` の事実を足した As-Is 文書（または `AGENTS.md`）と、checker の結果

## ルール

- 採否を決めない。Decision、Reason、Decided by を書かない。移す先の文書の frontmatter（`sourceRevision`、`reviewedAt` など）も書かない。更新するのは人。
- memory の引用をそのまま信じない。今のコードで確かめた行だけを根拠にする。
- 古い事実を正しく直して残したい場合は、新しい主張として根拠から書き直し、通常の採否に回す（この課題では行わない）。
- `fixtures/memories/memory-examples.jsonl` を `copilot memories import` で取り込まない。memory の保存先（GitHub の設定や `.github/copilot-memories.jsonl`）も変えない。
- `AGENTS.md` は checker の対象外。`AGENTS.md` に移した場合は、引用が合っているかを人が確かめる。

## 止める条件

- 手順4で一度止め、人の採否を待つ。
- 採られた行を文書に移し、checker が `pass` になったら止める。

## 確かめ方

```bash
node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/order-import-module-map.md
node .github/skills/as-is-documentation/scripts/check-human-fields.mjs --base HEAD --head-dir .
```

この課題で比べること：memory の引用を確かめるのは Copilot の判断で、保証ではない。Doc は、checker、drift 検出、特性テストという機械の検査と、人の採否で確かめる。
