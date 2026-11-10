---
owner: ""           # person or team responsible for this document (people fill it)
sourceRevision: ""  # commit the evidence was last checked against (people fill it at review)
reviewedAt: ""      # YYYY-MM-DD of the last human review
reviewBy: ""        # YYYY-MM-DD by which people re-read it, even without drift
---
<!-- code-to-doc-artifact
{
  "schemaVersion": 1,
  "kind": "batch-runbook",
  "scope": "{{SCOPE}}",
  "status": "draft"
}
-->
# {{TITLE}}

## Scope

{{READER_SCOPE_AND_EXCLUSIONS}}

## Execution prerequisites

- {{COMMAND_CREDENTIALS_OR_ENVIRONMENT}} [[source:{{PATH}}#L{{START}}-L{{END}}]]

## Input contract

| Item | Rule | Evidence |
|---|---|---|
| {{COLUMN_OR_FILE_RULE}} | {{RULE}} | [[source:{{PATH}}#L{{START}}-L{{END}}]] |

## Processing units and partial results

- {{UNIT_OF_WORK_AND_WHAT_PERSISTS_ON_FAILURE}} [[source:{{PATH}}#L{{START}}-L{{END}}]]

## Rerun decision table

| Situation | Operator action | Evidence |
|---|---|---|
| {{SITUATION}} | {{ACTION}} | [[source:{{PATH}}#L{{START}}-L{{END}}]] |

## Exit status and outputs

- {{EXIT_CODE_OR_OUTPUT_FILE}} [[test:{{TEST_FILE}}#{{TEST_ID}}]]

## Evidence

| Claim | Evidence |
|---|---|
| {{CLAIM}} | [[source:{{PATH}}#L{{START}}-L{{END}}]] |

## Unknowns

- U-1: {{UNKNOWN}} (next read: {{NEXT_READ_OR_OBSERVATION}})
