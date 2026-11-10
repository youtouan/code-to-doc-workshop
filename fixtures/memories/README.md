# Memory の例（4-1 の追加課題「Memory を Doc に昇格する」）

`memory-examples.jsonl` には、このリポジトリについての memory の例が3件、1行に1件ずつ入っています。手順は `docs/procedures/memory-promotion.md` を見てください。

## 3件の中身

今のコードで正しい事実、古くなった事実、memory にすべきでない作業メモが1件ずつ入っています。どれがどれかは、引用をコードで確かめて判断してください。講師用の想定は、このページの最後にあります。

## 形式について

- 各行の形は、Copilot CLI で memory を取り込むときの JSONL に合わせました。GitHub Docs の「Importing memories with Copilot CLI」の節で、2026-11-06 に確かめています。必須の項目は `type`、`version`、`scope`、`subject`、`fact`、`reason`、`citations` で、`externalId` は任意です。
- VS Code の Experimental の機能で memory をリポジトリに保存する `.github/copilot-memories.jsonl` の形式は、公開されている一次資料では確かめられませんでした。このファイルがその保存形式と同じだとは言えません。課題のための説明用の形式として扱ってください。
- `scope.repository` の `OWNER` と `REPOSITORY` は、わざと置き換えていません。
- 3件とも課題のために書いた例です。2件目は「以前は正しかった」と想定した例で、このアプリの過去の版の記述ではありません。

## してはいけないこと

- `copilot memories import` で取り込まない。古い事実や作業メモが、本物の memory としてリポジトリの共同作業者に共有されてしまいます。
- `.github/copilot-memories.jsonl` という名前でリポジトリに置かない。VS Code の設定によっては、エージェントがリポジトリの memory として扱う可能性があります。

## 出典

- [Manually creating memories for GitHub Copilot（Importing memories with Copilot CLI）](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/copilot-memory/create-memories)
- [About GitHub Copilot Memory（What Copilot does not store）](https://docs.github.com/en/copilot/concepts/prompting-and-context/copilot-memory)
- [Use memory with agents in VS Code（Store Copilot Memory in the repository (Experimental)）](https://code.visualstudio.com/docs/agents/run/memory)

<details>
<summary>講師用：想定の判断</summary>

| externalId | 確かめた結果 | 想定の判断 |
|---|---|---|
| MEM-001 | `BatchRunService.java` の54〜55行目は、却下された行があれば状態を `PARTIAL` にし、`PARTIAL` なら終了コードを3にしている。OBS-001 の終了コード3とも合う | 採る。As-Is 文書に根拠つきで移す |
| MEM-002 | `OrderGroup.java` の17行目は `MAX_LINES = 200`。49〜50行目は上限を超えたグループを `orderImport.groupLimit` で却下する。「100行」が今のコードと合わない | 採らない。memory は削除する |
| MEM-003 | コードの根拠がなく、その日の作業の状況だけを書いている | 採らない。Doc にも memory にも入れない |

</details>
