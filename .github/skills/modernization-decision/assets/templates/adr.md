---
owner: ""           # person or team responsible for this document (people fill it)
sourceRevision: ""  # commit the evidence was last checked against (people fill it at review)
reviewedAt: ""      # YYYY-MM-DD of the last human review
reviewBy: ""        # YYYY-MM-DD by which people re-read it, even without drift
---
# ADR-{{NUMBER}}: {{TITLE}}

- Status: proposed
- Scope: {{SCOPE}}
- Related As-Is documents: {{LINKS_TO_DOCS_AS_IS}}

## Context

{{CONTEXT_WITH_EVIDENCE_REFERENCES}}

## Options

| Option | Benefits | Costs and risks | Affected facts and unknowns | Behavior changes |
|---|---|---|---|---|
| Retain | {{...}} | {{...}} | {{...}} | None |
| Rehost | {{...}} | {{...}} | {{...}} | {{...}} |
| Replatform | {{...}} | {{...}} | {{...}} | {{...}} |
| Refactor | {{...}} | {{...}} | {{...}} | {{...}} |
| Rebuild | {{...}} | {{...}} | {{...}} | {{...}} |

## Characterization tests

- Must stay green: {{TEST_IDS}}
- Would need an approved change: {{TEST_IDS_AND_REASON}}

## Decision

<!-- Human only. Copilot must not fill this section. -->

## Rationale

<!-- Human only. -->

## Decided by / Date

<!-- Human only. -->
