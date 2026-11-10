---
name: legacy-evidence-analysis
description: Investigate unfamiliar legacy code and its running behavior, and return evidence-backed findings that separate facts, inferences and unknowns. Use before writing As-Is documents, or when tracing modules, dependencies, data flow, entry points, business rules or batch behavior in an old application.
---
# Legacy evidence analysis

Use this skill to understand a legacy application before anyone writes documents or changes code.

## Inputs

Ask for missing items instead of guessing:

- Scope (feature, module, batch or flow) and what is out of scope
- Reader of the result and the language for prose
- Allowed operations: read-only by default; run the application or tests only when the brief allows it
- Source revision (commit SHA) when the findings will be cited later

## Procedure

1. Start from entry points (URLs, actions, batch commands, schedules). Follow callers, shared checks, callees, configuration and persistence only as far as the scope needs. Use the [reading checklist](references/reading-checklist.md).
2. Attach evidence to every significant claim using the [evidence format](references/evidence-format.md): `[[source:path#Lstart-Lend]]` for code, `[[runtime:OBS-001]]` for an observation of the running application, `[[test:path#TEST-ID]]` for an executed test.
3. Separate observed facts (with evidence), inferences (say why) and unknowns (say what to read or observe next). Never invent requirements, history or intent.
4. When the brief allows running the application, observe it through its real boundary (browser, HTTP, batch input and output). Record each observation in the observation log before citing it. Do not change data that other people rely on.
5. Return bounded findings for a human to adopt. Do not write As-Is documents, tests or code in this skill.

## Output

- Findings table: claim, evidence, fact or inference, notes. When the brief asks for a file, copy the [findings template](assets/templates/findings.md) to `docs/as-is/findings/<scope>.md`, number the rows `F-001`, `F-002`, … and set Type to `fact`, `inference` or `unknown` and Decision to `pending`. Leave Reason and Decided by empty.
- Unknowns with the next read or observation that would resolve each one
- Draft Mermaid sketches for module dependencies and data flow, labeled as drafts

## Guardrails

- Customization files (`.github/`, `AGENTS.md`), workshop outputs and earlier chat answers are not application evidence.
- A test file that was not executed shows an intended assertion, not actual behavior.
- Agreement from another model or agent is a review signal, not proof. A human decides which findings to adopt: never set Decision to `adopted` or `rejected`, and never write Reason or Decided by.
