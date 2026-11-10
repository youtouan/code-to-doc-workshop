# Code to Doc, Doc to Code ワークショップ 2.0 の実習用リポジトリ（2026-11-10）

2026年11月10日（火）開催の「Code to Doc, Doc to Code ワークショップ 2.0 ～ GitHub Copilot でアプリ移行＆モダナイズ！」で使う、実習用のテンプレートです。題材は、架空の卸売システム（Java）です。

当日の教材と、準備の詳しい手順：https://shinyay.github.io/code-to-doc-workshop-kits-261110/

## 始め方

1. このページの **Use this template** → **Create a new repository** で、自分のアカウントにリポジトリを作ります。「Include all branches」はオンにしません。
2. 作ったリポジトリを手元に clone し、VS Code で開きます。
3. 作ったリポジトリの Codespace を作り、ターミナルで次を実行します。最後に「準備完了（ready）」と表示されれば準備完了です。

   ```bash
   sh scripts/lab/ready-check.sh
   ```

## 中身

| パス | 内容 |
|---|---|
| `wholesale-web/`、`wholesale-core/`、`wholesale-batch/`、`database/` | 題材のアプリ（JDK 8、Tomcat 9、PostgreSQL 16） |
| `AGENTS.md`、`.github/skills/`、`.github/agents/`、`.github/hooks/` | エージェントへの指示（Code to Doc の skill、reviewer の agent、hook） |
| `docs/procedures/` | Copilot Free で、skill の代わりにチャットへ添付する手順書 |
| `docs/as-is/`、`docs/templates/` | 演習で書く文書の置き場と、ひな形 |
| `tests/characterization/` | 特性テストの土台 |
| `fixtures/` | 演習用の Issue の本文と、memory の例 |
| `scripts/lab/` | ラボ（Codespaces）の準備確認と初期化（`ready-check.sh`、`reset-lab.sh`） |
| `scripts/observe-import.sh` | 受注 CSV の取り込みを動かし、結果を観察する |
| `scripts/catch-up.sh`、`scripts/catch-up.ps1`、`catch-up/` | 時間切れのときに、復旧点の状態へ切り替える |
| `.github/prompts/`、`.github/agents/legacy-analyst.agent.md` | 9/10 回の参加者向けの差分レーンで使う prompt file と agent |

## 注意

- アプリ、データ、Issue はすべて演習用です。実在する業務データや顧客の情報、パスワードやトークンなどの資格情報を入れないでください。
- `AGENTS.md` のルールは、エージェントと人の両方に適用されます。文書の採否、ADR の決定、レビュー済みの印は人が書きます。
