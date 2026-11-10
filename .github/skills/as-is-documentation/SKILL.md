---
name: as-is-documentation
description: Write As-Is design documents (module map, feature spec, process design, batch runbook) from adopted evidence, using Markdown and Mermaid with checked citations and explicit unknowns. Use when turning legacy analysis into documents under docs/as-is, and run the bundled checker before handing a draft to a human.
---
# As-Is documentation

## Inputs

- Adopted findings (rows with Decision `adopted` in `docs/as-is/findings/*.md`, from `legacy-evidence-analysis`, or from a human), scope, reader and prose language
- Document kind: `module-map`, `feature-spec`, `process-design` or `batch-runbook`
- One output path under `docs/as-is/`

## Procedure

1. Start from the template for the kind: [module map](assets/templates/module-map.md), [feature spec](assets/templates/feature-spec.md), [batch runbook](assets/templates/batch-runbook.md). For `process-design`, use the sections listed in [the contract](references/artifact-contract.json) and the same frontmatter. Keep the YAML frontmatter, the metadata comment and the exact `##` headings. Write prose in the requested language.
2. Use only adopted evidence. Every list item and table row in the body sections needs at least one reference: `[[source:...]]`, `[[runtime:...]]`, `[[test:...]]` or `[[unknown:U-n]]`. Put unsupported points in `## Unknowns` as `- U-n: ... (next read: ...)`. An unknown may end with a status tag: ` [status: investigating]` while someone works on it; only people write ` [status: resolved]` or ` [status: accepted]` (kept as an unknown on purpose).
3. Diagrams: `module-map` needs Mermaid in `## Dependencies` and `## Data flow`; `process-design` needs Mermaid in `## Call sequence`. Add at least one cited line under each diagram.
4. Keep `status` as `draft`. Only a human changes it to `reviewed`, after writing `owner` in the frontmatter.
5. Leave the frontmatter values as they are. People fill `owner` (the person or team responsible), `sourceRevision` (the commit the evidence was checked against), `reviewedAt` and `reviewBy` (dates as YYYY-MM-DD) when they review.
6. Run the checker and report its actual result:
   - From the plugin: `node <skill-directory>/scripts/check-evidence.mjs --root . docs/as-is/<file>.md`
   - From a repository that copied this skill: `node .github/skills/as-is-documentation/scripts/check-evidence.mjs docs/as-is/<file>.md`
   If you cannot run commands, say "not run". Never report a result you did not see.
7. Hand over the draft, the checker result and the open unknowns to a human or the `evidence-reviewer` agent.

## Result meanings

- `pass` (exit 0): frontmatter, metadata, layout and every evidence target are valid. It does not prove that the claims are true or complete.
- `invalid` (exit 1): fix each reported line. Never delete evidence or unknowns just to pass. `owner-missing` (a reviewed document without `owner`) is for a person to fix: report it, do not fill the owner.
- `uncheckable` (exit 3): no target, bad arguments or unreadable input. Fix the input; it is not a pass.
- Warnings do not change the result: `uncited-prose`, `frontmatter-date` (`reviewedAt` or `reviewBy` is not YYYY-MM-DD) and `review-overdue` (`reviewBy` has passed, so people re-read the document even if nothing drifted). `--today YYYY-MM-DD` sets the date that `reviewBy` is compared with.

## Human-owned fields

`scripts/check-human-fields.mjs --base <ref> --head <ref>` (or `--base-dir`/`--head-dir`) lists changes to fields only people decide: finding Decision/Reason/Decided by, Unknowns marked resolved or accepted, ADR Status and the Decision/Rationale/Decided by sections, `"status": "reviewed"`, and the frontmatter fields `owner`, `sourceRevision`, `reviewedAt` and `reviewBy`. Exit 0 none, 2 changed, 3 uncheckable. Never write these fields yourself; if the script reports your change, revert it. Repositories enforce this with CODEOWNERS, required reviews and rulesets.

## Hook

The plugin's `agentStop` hook runs `scripts/notify-evidence.mjs` after each agent turn and reports the result for changed documents under `docs/as-is/`. It only notifies by default. With `CODE_TO_DOC_HOOK_MODE=block`, it asks the agent for one more turn when a changed document is invalid, and it never blocks a turn that a previous block already forced or when every problem is `owner-missing` (only a person can fix it).
