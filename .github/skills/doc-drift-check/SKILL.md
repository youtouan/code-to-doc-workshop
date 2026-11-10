---
name: doc-drift-check
description: Detect As-Is document claims whose cited source lines changed in a pull request or working tree, and propose document updates. Use on pull requests that touch legacy code, in agentic workflows or scheduled automations, and before handing over a modernization change.
---
# Doc drift check

## Procedure

1. Run the deterministic detector from the repository root:
   - Pull request or branch: `node <skill-directory>/scripts/doc-drift.mjs --base origin/main --head HEAD`
   - Uncommitted work: `node <skill-directory>/scripts/doc-drift.mjs --base HEAD --working-tree`
   - Add `--json` for machine-readable output and `--docs <dir>` when documents are not in `docs/as-is`.
   - In a repository that copied this skill, the script is `.github/skills/doc-drift-check/scripts/doc-drift.mjs`.
2. Read the exit code: `0` no cited evidence was touched, `2` drift found, `3` uncheckable (fix the input; it is not a pass).
3. Handle findings in this order: `file-removed`, `content-changed`, `file-renamed`, `line-shift`. For each one, re-read the cited code at the new revision and propose one of: update the claim and its line range, move the claim to `## Unknowns`, or ask a human to re-review the document.
4. In a pull request, post one summary comment with the report table and the proposed edits. Do not push changes unless someone asks you to.

The detector sees only cited code that moved or changed (citation drift). Behavior that changed through settings or data needs the characterization tests and observations run again. A document whose `reviewBy` date has passed needs re-reading even without drift; the As-Is checker warns about it as `review-overdue`.

## Guardrails

- Never edit cited source code to match a document.
- Never mark a document `reviewed` or delete unknowns to make drift disappear.
- Never edit the frontmatter (`owner`, `sourceRevision`, `reviewedAt`, `reviewBy`). The person who accepts an update records the new revision and review date.
- `line-shift` usually needs only a new line range, but confirm that the cited lines still say the same thing.
