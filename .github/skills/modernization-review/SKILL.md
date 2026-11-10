---
name: modernization-review
description: Review pull requests that modernize legacy code against AGENTS.md rules, accepted ADRs, characterization tests and As-Is documents. Use in Copilot code review or a local review to find unapproved behavior changes, weakened tests, missing document updates and scope creep.
---
# Modernization review

Review the pull request with the [review checklist](references/review-checklist.md).

## How to report

- Write concrete comments with file and line.
- Separate **blocking** findings (behavior change without an accepted ADR, weakened or deleted characterization test, deleted evidence or unknowns, edits to legacy code outside the stated scope) from suggestions.
- When a finding depends on a document, cite it (`docs/as-is/...`, `docs/adr/...`).
- Passing checks are not proof of correctness. Say what the tests do not cover.

## Guardrails

- Do not approve on behalf of a person, and do not push changes from a review.
- Do not treat an AI-generated ADR draft (status `proposed`) as approval.
