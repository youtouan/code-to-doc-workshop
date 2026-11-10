---
owner: ""           # person or team responsible for this document (people fill it)
sourceRevision: ""  # commit the evidence was last checked against (people fill it at review)
reviewedAt: ""      # YYYY-MM-DD of the last human review
reviewBy: ""        # YYYY-MM-DD by which people re-read it, even without drift
---
<!-- code-to-doc-artifact
{
  "schemaVersion": 1,
  "kind": "feature-spec",
  "scope": "{{SCOPE}}",
  "status": "draft"
}
-->
# {{TITLE}}

## Scope

{{READER_SCOPE_AND_EXCLUSIONS}}

## Actors and preconditions

- {{ACTOR_AUTHORIZATION_OR_PRECONDITION}} [[source:{{PATH}}#L{{START}}-L{{END}}]]

## Inputs

| Input | Validation and defaults | Evidence |
|---|---|---|
| {{FIELD}} | {{RULE}} | [[source:{{PATH}}#L{{START}}-L{{END}}]] |

## Rules and state

- {{RULE_OR_STATE_TRANSITION}} [[source:{{PATH}}#L{{START}}-L{{END}}]]

## Outputs and side effects

- {{OUTPUT_DATA_UPDATE_OR_EXTERNAL_EFFECT}} [[source:{{PATH}}#L{{START}}-L{{END}}]]

## Failure cases

- {{REJECTION_FAILURE_OR_PARTIAL_RESULT}} [[runtime:{{OBSERVATION_ID}}]]

## Evidence

| Claim | Evidence |
|---|---|
| {{CLAIM}} | [[test:{{TEST_FILE}}#{{TEST_ID}}]] |

## Unknowns

- U-1: {{UNKNOWN}} (next read: {{NEXT_READ_OR_OBSERVATION}})
