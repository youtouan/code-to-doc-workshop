---
name: "analyze-feature"
description: "Analyze a bounded feature with the read-only analyst"
argument-hint: "scope, reader, source SHA, configuration SHA, allowed operations"
agent: "legacy-analyst"
---
# Analyze with an explicit role

Select [legacy-analyst](../agents/legacy-analyst.agent.md) explicitly. Ask for scope, reader, source/configuration SHAs, allowed operations and response location when missing.
Locate entry points, shared checks, callees, configuration and persistence. Return claims with paths, symbols and bounded line ranges, separating inferences and unknowns.
Cover success, rejection, state and side effects. Analysis-only work stays in chat and does not activate a document check.
The human selects evidence and reviews the handoff to the `as-is-documentation` skill. No tool override or model switch is requested here.
