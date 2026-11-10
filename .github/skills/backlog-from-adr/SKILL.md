---
name: backlog-from-adr
description: Turn one accepted ADR and the As-Is documents it relies on into epic, feature and task drafts for GitHub Issues. Runs only when a person invokes it, and creates issues only after that person confirms.
argument-hint: "[ADR path] [As-Is document paths]"
disable-model-invocation: true
---
# Backlog from an accepted ADR

This explicitly invoked skill replaces the prompt file `backlog-from-adr.prompt.md` from code-to-doc-kit 0.1. The Copilot harness in VS Code does not load prompt files, so reusable workflows live in skills.

## Inputs

- The ADR path, for example `docs/adr/0001-order-registration.md`
- One or more As-Is documents that the ADR relies on, for example `docs/as-is/order-module-map.md`

Ask for any missing input. Do not guess paths.

## Procedure

1. Read the ADR. If its status is not `accepted`, stop and say that a person must decide first.
2. Split the work into one epic, two to four features and small tasks. Each task must fit in one pull request and name the characterization tests that must stay green.
3. For each item, write: title, purpose, scope (in and out), acceptance criteria, evidence links (As-Is sections and test IDs) and open unknowns.
4. Show the drafts in chat. Create GitHub Issues only after the person confirms, and only for the items they confirm.

## Guardrails

- Never edit the ADR, its status or its decision fields.
- Write acceptance criteria as observable behavior or as characterization test IDs.
- Carry over unknowns from the ADR and the As-Is documents. Do not resolve them by assumption.
