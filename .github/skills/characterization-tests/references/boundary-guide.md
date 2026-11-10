# Choosing a boundary

Pick the boundary that both the legacy system and the modernized system must keep. The further out the boundary, the longer the test survives.

| Boundary | Good for | What to pin | Watch out for |
|---|---|---|---|
| HTTP | Web actions and APIs | Status, redirects, key fields, messages | Sessions and login, CSRF tokens, timestamps |
| Visible messages | Input rules shown to users | Error and confirmation messages, field errors | Layout and styling are not behavior |
| Batch files | Imports, exports, nightly jobs | Output files, rejection files, exit codes | Row ordering, run IDs, dates in file names |
| Database state | Persistence rules | Rows before and after an operation | Shared data; use a dedicated database and reset it |

## Practical rules

- One test pins one observable rule. Name it `CHAR-<AREA>-<NNN> <what it pins>`.
- Keep inputs in files next to the tests (for example `tests/characterization/fixtures/`) so reviewers can read them.
- When a value is truly non-deterministic, replace it with a placeholder in a `normalize` function and explain why in a comment.
- Record how to start the legacy system in the test file header (command, port, data reset).
- If the repository already has smoke scripts or sample files (for example `scripts/http-*.mjs` or `samples/*.csv`), reuse their login and data setup instead of inventing new ones.
