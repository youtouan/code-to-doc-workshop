---
name: release-radar
description: Build a dated, evidence-backed radar of GitHub Changelog posts and VS Code release notes since a given date, for teams doing Code to Doc and Doc to Code work. Use when someone asks what changed since the last workshop, sprint or review, or needs to refresh a feature availability table.
---
# Release radar

A radar lists recent product changes. Each row has a source URL, the source's own status words and the date or version. Collecting is a script; choosing and summarizing the items is your job.

## Inputs

- Since date (required). Until date (default: today).
- Themes to keep. Default: documents and diagrams, agent customization (instructions, AGENTS.md, skills, custom agents, hooks, plugins), planning and review, cloud agent and code review, automations and workflows, modernization and upgrades, and organization policies that can block any of these.
- Output folder. Default: `docs/radar/`.

## Procedure

1. Collect: `node scripts/collect-releases.mjs --since <date> --until <date> --out docs/radar/<checked date>.collection.json`. It reads only `github.blog` and `raw.githubusercontent.com`. If the network is blocked, for example in a sandbox, ask the person to run it or to allow those two hosts. Never write a radar from memory.
2. If the result is not `done`, stop and report which source failed.
3. Read the collection and keep the items that match the themes. Open the source page when the excerpt is not enough to judge.
4. Copy the [template](assets/templates/radar.md) to `docs/radar/<checked date>.md` and write one row per change, following the [rules](references/radar-rules.md).
5. Check: `node scripts/check-radar.mjs docs/radar/<checked date>.md`. Fix every error. Treat each warning as a question to answer, not a message to silence.
6. Report how many items you collected and kept, what you added by hand, and which rows may affect the team's materials, settings or availability tables. Do not edit those materials yourself.

## What the checker proves

`pass` means every row cites an item from the collection (or sits in the manual section), copies that item's status words, and names the right date or version. It does not prove that you chose the right items or summarized them well.
