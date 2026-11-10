# AGENTS.md

These rules apply to every agent working in this repository (local agents, Copilot cloud agent and Copilot code review) and to people.

## Purpose

This repository is being modernized with evidence-backed Code to Doc and Doc to Code. Agents may analyze, document, implement and review, but people decide what the system should do.

## Sources of truth

- Current behavior: `docs/as-is/` (documents with citations) and `tests/characterization/`
- Findings to adopt: `docs/as-is/findings/` — people decide which findings are adopted
- Decisions: `docs/adr/` — only ADRs with `Status: accepted` authorize behavior changes
- Open questions: the `## Unknowns` section of each As-Is document

## Rules

1. Keep characterization tests green. Do not edit, skip or weaken them unless an accepted ADR names the change.
2. Do not change observable behavior (HTTP responses, messages, batch outputs, stored data) without an accepted ADR.
3. When you change code that an As-Is document cites, run the drift check and update or flag the affected claims in the same pull request.
4. Never invent evidence. Cite code as `[[source:path#Lstart-Lend]]` and put unsupported points in `## Unknowns`.
5. Never fill ADR decisions, adopt or reject findings, mark unknowns as `resolved` or `accepted`, approve on behalf of people, mark documents as `reviewed`, or write the document frontmatter (`owner`, `sourceRevision`, `reviewedAt`, `reviewBy`). These decisions are written by people, in the repository, so that they can be reviewed in pull requests.
6. Keep each pull request inside the scope of its issue or ADR.

## Commands

- Build and existing tests: `mvn -B -ntp verify`
- Tests that need the database: `mvn -B -ntp verify -Ddb.tests=true -Ddb.url=jdbc:postgresql://127.0.0.1:5432/wholesale_test -Ddb.username=wholesale -Ddb.password=$PGPASSWORD`
- HTTP smoke test of the running app: `node scripts/http-smoke.mjs http://127.0.0.1:8080/wholesale`
- Characterization tests: `node --test "tests/characterization/**/*.test.mjs"`
- Document check: `node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/<file>.md`
- Drift check: `node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/main --head HEAD`

## Never

- Use production data, real credentials or shared environments
- Let an agent push directly to the default branch. Agents open pull requests; people decide what lands on the default branch.
