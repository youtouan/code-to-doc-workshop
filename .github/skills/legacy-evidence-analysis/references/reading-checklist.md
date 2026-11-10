# Reading checklist

| Lens | Questions to answer with evidence |
|---|---|
| Entry and authority | What reaches this path (URL, action, batch command, schedule)? Which shared checks apply, and to whom? |
| Conditions and state | Which preconditions, branches, per-item outcomes and aggregate decisions exist? |
| Data and configuration | Which mappings, constraints, injected dependencies and transaction policies support the claim? |
| Failure scope | Is a rejection local to a row, group, operation or the whole input? What can persist after a failure? |
| Batch operations | How do input identity, run identity, terminal and in-progress states, processing units, limits and reruns interact? |
| Runtime behavior | What does the running application actually show or output for this path (screens, messages, files, exit codes)? Does it match the code reading? |
| Observability | Which returned values, persisted status, exit codes and output files can an operator inspect? |
| Evidence quality | Does each cited span support the claim at the stated revision? Is a test definition being confused with an executed test? |
| Uncertainty | What is inferred, missing or untested? Which next read or safe observation would resolve it? |

Use only the lenses the scope needs. Do not turn these questions into assumed business rules.
