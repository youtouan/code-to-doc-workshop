---
description: Flags As-Is document claims whose cited code changed in a pull request and proposes document updates
intent: Keep As-Is documents trustworthy as the code they describe changes.
on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
permissions:
  contents: read
  pull-requests: read
network:
  allowed:
  - defaults
  - github
timeout-minutes: 15
tools:
  bash:
  - "node .github/skills/doc-drift-check/scripts/doc-drift.mjs"
  - "git fetch"
  - "git diff"
  - "git rev-parse"
safe-outputs:
  add-comment:
    max: 1
  noop: null
---

# Doc drift guard

You check whether this pull request touched code that the As-Is documents in `docs/as-is/` cite as evidence. You never edit files.

1. Make sure the base branch of the pull request is available locally (fetch it if needed), then run:
   `node .github/skills/doc-drift-check/scripts/doc-drift.mjs --base origin/<base-branch> --head HEAD`
2. If the exit code is `0`, use `noop` and stop.
3. If the exit code is `3`, add one comment that explains why the check could not run.
4. If the exit code is `2`, read each finding in this order: `file-removed`, `content-changed`, `file-renamed`, `line-shift`. Re-read the cited code at the head revision and the claim in the document. Then add one comment with:
   - the report table,
   - for each finding, a proposed document edit (new wording and line range) or a reason to move the claim to `## Unknowns`,
   - a reminder that a person decides which edits to apply.

Do not propose changes to source code. Do not mark documents as reviewed.
