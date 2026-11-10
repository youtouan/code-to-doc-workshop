# Evidence format

| Kind | Token | Rules |
|---|---|---|
| Source code | `[[source:path/from/repo/root#L12-L18]]` | Repository-relative path with `/`. At most 200 lines per citation. Files under `.github/`, `.runtime/` and `docs/as-is/` are not application evidence. |
| Runtime observation | `[[runtime:OBS-001]]` | The ID must exist in the observation log (default `docs/as-is/observations.md`). |
| Executed test | `[[test:path/to/test-file#CHAR-ORD-001]]` | The ID must appear in that file, and the test must have been run. |
| Unknown | `[[unknown:U-1]]` | Must be defined in `## Unknowns` as `- U-1: ...`. |

A single line may be cited as `#L42`.

## Observation log

Keep runtime observations in `docs/as-is/observations.md`, one row per observation. Record what was seen, not the interpretation, and enough context to repeat it.

| ID | When | Environment | Steps | Observed result | Artifact |
|---|---|---|---|---|---|
| OBS-001 | 2026-11-10 13:40 | Codespaces, legacy app on port 8080 | Submit an order with quantity 0 | The page shows the message "数量は1以上で入力してください" | screenshots/obs-001.png |

The example row shows the shape only; replace it with real observations.
