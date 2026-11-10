---
name: characterization-tests
description: Pin the current behavior of a legacy application with characterization (golden master) tests at boundaries that survive modernization, such as HTTP, screen messages, batch CSV input and output, or database state. Use before refactoring, upgrading or rewriting legacy code, and to turn As-Is documents into executable checks.
---
# Characterization tests

Characterization tests record what the system does today, including odd behavior. They become the acceptance gate for modernization: the new implementation must produce the same observable results unless an accepted ADR approves a change.

## Inputs

- The behavior to pin (an As-Is claim, rule or flow) and its evidence
- The boundary to test (see the [boundary guide](references/boundary-guide.md))
- How to start the legacy application and reset its data, if the test needs a running system

## Procedure

1. Choose the outermost boundary that the modernized system must keep: HTTP request and response, visible messages, batch input file to output file and exit code, or database rows. Avoid internal classes of the legacy code; such tests cannot survive a rewrite.
2. Start each test name with an ID such as `CHAR-ORD-001`, so documents can cite it as `[[test:path#CHAR-ORD-001]]`.
3. Write expectations from what the legacy system actually returns. Run the test against the legacy system first and keep it green. If the current behavior looks wrong, keep the expectation, add a comment, and record the doubt as an unknown or defect candidate. Do not "fix" behavior inside a test.
4. Normalize only real noise (timestamps, generated IDs, ordering the contract does not promise) and document every normalization in the test.
5. Start from the [test template](assets/templates/characterization.test.mjs) (Node.js `node:test`, no dependencies). Put tests under `tests/characterization/`.
6. Report the command you ran and the actual result. A test that was not run is not evidence.

## Guardrails

- Do not change legacy application code to make a characterization test pass.
- Do not weaken an assertion so that a modernized implementation passes. That needs an accepted ADR and a human decision.
- Use a dedicated database or reset data between runs; never point tests at shared or production data.
