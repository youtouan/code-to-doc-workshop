---
name: code-to-doc-env-check
description: Check, without changing anything, whether a repository and the local environment are ready for Code to Doc and Doc to Code work - repository customizations, document folders, git, GitHub CLI, the lab codespace and app, and optional tools - and report what to fix. Use at the start of a workshop or before introducing the toolkit to a repository.
---
# Code to Doc environment check

This check is read-only. Do not change files, settings, branches or codespaces. Do not create or start a codespace; it can cost money. Report every check as `OK`, `NG` or `未確認`, with the evidence you actually saw.

## Checks

| # | Check | How |
|---|---|---|
| 1 | Git repository with a GitHub remote | `git rev-parse --show-toplevel`, `git remote -v`, `git status -sb` |
| 2 | Default branch is `main` | `gh repo view --json defaultBranchRef --jq .defaultBranchRef.name`, or without the GitHub CLI `git symbolic-ref refs/remotes/origin/HEAD`. The catch-up script, the drift check (`--base origin/main`) and the pull requests assume `main`; any other name is `NG`. |
| 3 | Repository customizations | These files exist: `AGENTS.md`, `.github/skills/legacy-evidence-analysis/SKILL.md`, `.github/skills/as-is-documentation/SKILL.md`, `.github/skills/characterization-tests/SKILL.md`, `.github/skills/doc-drift-check/SKILL.md`. Missing files mean the toolkit has not been exported yet. |
| 4 | Document folders | `docs/as-is/`, `docs/as-is/findings/`, `docs/adr/`. A missing folder before the first exercise is `OK (作成前)`. |
| 5 | Old prompt files | List `.github/prompts/*.prompt.md`. The Copilot harness does not load prompt files; report them as candidates for skill migration, not as errors. |
| 6 | Checker | `node --version` (22 or later), then `node .github/skills/as-is-documentation/scripts/check-evidence.mjs --help`. If Node.js is missing, report `未確認` and say that the checker runs in the lab or in CI. |
| 7 | GitHub CLI and push permission | `gh --version`, `gh auth status`, and `gh repo view --json viewerPermission --jq .viewerPermission`. `WRITE`, `MAINTAIN` or `ADMIN` is needed to push branches and open pull requests. |
| 8 | Lab codespace | `gh codespace list --repo <owner/repo>` shows a codespace. Report its state; do not start it. |
| 9 | Lab app URL reachable | Ask the person for the app URL (for example a forwarded `http://localhost:8080/...` login page). Request it once and report the HTTP status; 2xx or 3xx is `OK`. Without a URL, report `未確認`, never `OK`. |
| 10 | Copilot plan and permissions note | You cannot see the plan. Ask the person which Copilot plan they use (Free or a paid plan through an organization) and whether organization policies block features (cloud agent, code review, MCP, preview features). Write the answer down as a note: Free runs the core with procedure documents instead of skills, and the paid options become reading the worked examples. Accounts in an organization cannot use Free. |
| 11 | VS Code session | You cannot see the editor's settings. Ask the person to confirm the VS Code desktop version (Help > About), that the Session Target is Copilot, and, only if they want the optional board, that `chat.canvases.enabled` is on. Never guess these values. |

If a command is blocked, for example by the sandbox or a missing tool, report `未確認` and show the command the person can run.

## Required checks per chapter

A section can start only when every check listed for it is `OK`. `未確認` counts as not ready.

| Section | Required checks | Optional |
|---|---|---|
| Readiness Clinic | 1, 2, 10, 11 | — |
| Chapter 1 (Code to Doc: 1-1, 1-2, 1-4) | 1, 3, 4, 11 | 6 (the checker can run in the lab) |
| Chapter 1 runtime observation (1-3) | 8, 9 | — |
| Chapter 2 (characterization tests, ADR, AGENTS.md) | 1, 3, 4, 8, 9 | 6 |
| Chapter 3 (Doc to Code, pull requests) | 1, 2, 7, 8, 10 | cloud agent and code review depend on the plan in 10 |
| Chapter 4 (drift check) | 1, 2, 3, 6 | the board (11, `chat.canvases.enabled`) |

Chapter 5 is a talk and needs no checks.

## Report

1. A table: check, result, evidence.
2. A table: section, `進められる` or `進められない`, and for each section that cannot proceed, the checks that blocked it. Always list the sections that cannot proceed; do not leave them for the person to work out.
3. What to fix first, in order, with the exact command or setting.
4. What is optional (Node.js on the desk, the Canvas, the paid-plan options) and what is required for the core exercises.
