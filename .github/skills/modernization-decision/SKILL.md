---
name: modernization-decision
description: Draft an architecture decision record (ADR) that compares modernization options for one legacy scope using As-Is evidence and unknowns, without making the decision. Use when choosing whether to retain, rehost, replatform, refactor or rebuild part of a legacy application.
---
# Modernization decision (ADR draft)

## Inputs

- Scope of the decision (one module, flow or batch)
- As-Is documents, characterization tests and open unknowns for that scope
- Constraints from people: deadline, budget, skills, operations, compliance
- Candidate options (default: retain, rehost, replatform, refactor, rebuild)

## Procedure

1. Copy the [ADR template](assets/templates/adr.md) to `docs/adr/NNNN-<slug>.md`. Leave its YAML frontmatter (`owner`, `sourceRevision`, `reviewedAt`, `reviewBy`) empty for people.
2. For each option, list benefits, costs and risks, the As-Is facts and unknowns that affect it (with references), and the observable behavior it would change.
3. List the characterization tests that must stay green, and the ones an option would require changing.
4. Keep `Status: proposed`. Leave **Decision**, **Rationale** and **Decided by / Date** empty for a human.
5. Do not hide options or rank them by preference. If people ask for a recommendation, give it in a separate note with its assumptions.

## Guardrails

- Never fill the decision, mark an ADR accepted, or write a person's name or date, including in the frontmatter.
- Behavior changes are allowed only after a human accepts the ADR.
