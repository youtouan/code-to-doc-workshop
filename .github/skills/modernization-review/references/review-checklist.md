# Modernization review checklist

| Area | Check | Blocking when |
|---|---|---|
| Scope | Does the change stay inside the issue or ADR scope? | Unrelated legacy files change without explanation |
| Behavior | Does any observable behavior change (HTTP response, message, batch output, data)? | The change has no accepted ADR in `docs/adr/` |
| Characterization tests | Are tests in `tests/characterization/` unchanged and passing? | An assertion is weakened, skipped or deleted without an accepted ADR |
| Evidence | Do changed As-Is documents keep citations and unknowns? | Evidence or unknowns are deleted to make a check pass |
| Doc drift | Did the change touch code cited by `docs/as-is/`? Were the affected claims updated or flagged? | Cited code changed and the documents were neither updated nor flagged |
| Decisions | Are ADR decisions left to people? | An agent filled Decision, Rationale or Decided by |
| Tests for new code | Do new tests cover the new implementation and its failure cases? | — (suggestion) |
| Security and data | Are credentials, production data and shared environments untouched? | Secrets or production endpoints appear in the change |

Green checks show only what the tests cover. Name the remaining risks in the review summary.
