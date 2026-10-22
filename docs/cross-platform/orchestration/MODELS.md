# Worker model roster (opencode free models)

Chosen from **public ratings** (user instruction 2026-10-22: no trials). Sources are third-party aggregators and vendor posts, and they disagree in places, so treat the numbers as rough. Re-check if a model misbehaves.

| Tier | Use for | Primary | Fallback |
|---|---|---|---|
| A | Gradle/KMP config, data layer, navigation, big screens (M2-01, 06–09, 12–15) | `opencode/deepseek-v4.1-flash-free` | `opencode/glm-5.3-flash-free` |
| B | Mechanical moves of components/screens (M2-03–05, 10–11) | `opencode/glm-5.3-flash-free` | `opencode/nemotron-3-ultra-free` |
| C | Bulk text transforms (big resource files), long-context reading | ~~`opencode/beam-free`~~ → `opencode/glm-5.3-flash-free` | orchestrator script |

## Evidence
| Model | Public ratings found (Oct 2026) |
|---|---|
| deepseek-v4.1-flash | Terminal-Bench 2.1 90.6% (BenchLM, 3rd overall); Artificial Analysis Intelligence Index 39–45 depending on version (the "1017" update is 45, ahead of GLM-5.3); a reported #1 on LiveBench agentic coding (third-party blog). |
| glm-5.3-flash | Terminal-Bench 2.1 88.2% (BenchLM, 8th); AA Intelligence Index 42. |
| nemotron-3-ultra | SWE-bench Verified 71.9%, Terminal-Bench 2.0 56.4%, AA Coding Index 49.3 (BenchLM comparison pages; no primary source found). |
| mimo-v2.6-flash | AA Intelligence Index 38 (lowest of the rated ones). Not used. |
| minimax-m3.1-flash | Vendor claims DeepSWE 73.7%; aggregators conflict (BenchLM coding 61.3 vs LMMarketCap 40/100). Not used until the evidence agrees. |
| beam (Reflection Beam 1.0, 501B-A23B, 1M ctx) | No coding benchmarks found. The free tier keeps data 30 days. Only for low-risk bulk work. |
| flint-40b, nemotron-3.5-lightning, nemotron-3.5-super-vl | No public ratings found. Not used. |
| opencode-go/* | Exited immediately in a smoke run (probably needs an OpenCode Go subscription). Not available. |

A partial trial was run before the user stopped it (single task, hidden tests; anecdotal): nemotron-3-ultra 18/18, deepseek/beam/glm 17/18, minimax errored, flint and nemotron-3.5-super-vl didn't produce compiling code. This is consistent with the roster.

Sources: benchlm.ai (Terminal-Bench 2.1, SWE-bench Verified, model comparisons), artificialanalysis.ai (model comparisons, changelog), officechai.com (DeepSeek V4.1 Flash 1017 on AA), minimax.io/blog/minimax-m31f, lmmarketcap.com, orcarouter.ai (Beam 1.0 on opencode), opencode.ai/docs/zen.

## Observed in practice
- 2026-10-22, **beam**: 2/2 attempts on M2-02 produced no changes (over-exploration, repeated `/tmp` access despite rules). Demoted; don't use.
- 2026-10-22, **deepseek-v4.1-flash**: M2-01 done in one attempt, in scope, CI green.
