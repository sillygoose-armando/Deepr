# M2+ orchestration: Claude orchestrates & verifies, opencode free models implement

User instruction (2026-10-22): delegate implementation to opencode free models chosen by quality/capability; the orchestrator (Claude) plans, reviews, verifies and commits. Use codegraph. State must survive interruptions of workers and of the orchestrator.

## Where state lives (all in git on `feature/kmp`, worktree `/workspace/tmp/app-teamlinkr/deepr-kmp`)
| File | What |
|---|---|
| `QUEUE.md` | The task queue: one row per task with status, assignee model, attempts, commit, CI run. **Single source of truth for "what's next".** |
| `runs.tsv` | Append-only worker run log (start/end/resume, model, opencode session id, exit code, raw log path). Written by `tools/orchestrate/dispatch.sh`. |
| `MODELS.md` | Model trial results and the roster (which model gets which kind of task). |
| `../tasks/<TASK>.md` | Self-contained brief per task (scope, codegraph context, acceptance commands) + a `## Worker log` section the workers append to + `## Review` notes by the orchestrator. |
| `WORKER_RULES.md` | Rules every worker reads. |
| `../PROGRESS.md`, `../DECISIONS.md` | Milestone-level progress and decisions (brief §10). |
| `.orchestration/logs/` (untracked) | Raw opencode JSON logs. Disposable; the summary lives in the Worker log. |

## Task lifecycle (status values in QUEUE.md)
`todo` → `running` (worker dispatched; commit QUEUE.md first) → `review` (worker finished) → either `changes` (re-dispatch with review notes, attempts+1) or `ci` (orchestrator committed + pushed, waiting for CI) → `done` (CI green; commit hash + run URL recorded). `blocked` = needs the user (question written in the brief).
Escalation: after 2 failed attempts with a model, move to the next model in the roster's tier list; after 3 total failures, the orchestrator implements it directly and notes why.

## Orchestrator loop
1. Pick the first `todo`/`changes` task in QUEUE.md whose dependencies are `done`.
2. Write/refresh its brief using codegraph (`codegraph explore`, `codegraph impact`), set status `running` + model, **commit** (`docs(orch): dispatch <task>`).
3. `tools/orchestrate/dispatch.sh <TASK> <model>` (background).
4. On finish: review `git diff` against the brief, run the local checks, fix trivial issues or re-dispatch with notes. Then commit with message `<type>(kmp): <task> ...` (+ `Co-Authored-By` line), push, watch CI (`gh run watch`).
5. Record the result in QUEUE.md (+ PROGRESS.md for milestone items), commit `[skip ci]`.

## Resume protocol (after any interruption)
1. `cd /workspace/tmp/app-teamlinkr/deepr-kmp && git status && git log --oneline -10`.
2. Read QUEUE.md. For a task marked `running`:
   - `pgrep -fa "opencode run"`: if a worker is still alive, wait for it.
   - Otherwise check the last line for that task in `runs.tsv`. If there's a `start` but no `end`, or `exit≠0`, the worker was interrupted. Look at `git diff` and the brief's Worker log, then either resume it with `tools/orchestrate/dispatch.sh <TASK> <model> --resume <session-id>` (the session id is in the `end` row or in the raw log: `grep -o '"sessionID":"[^"]*"' <log> | head -1`) or re-dispatch from scratch after discarding its partial changes (`git checkout -- <files>`, logged in the brief).
3. For a task in `ci`: `gh run list -R sillygoose-armando/Deepr --branch feature/kmp --limit 3`.
4. Continue the orchestrator loop.

## Environment
- Local: `./gradlew :shared:compileKotlinJvm` (+ `:ui:compileKotlinJvm` once it exists) works thanks to a minimal SDK (`~/android-sdk-min`, platform android-36 only; `local.properties` is untracked). Android variants/APK and iOS: CI only.
- opencode workers run with `--pure` (the global oh-my-opencode plugin hangs headless runs), and with codegraph MCP from the worktree's untracked `opencode.json`. If that file is missing, recreate it:
  `{"$schema":"https://opencode.ai/config.json","mcp":{"codegraph":{"type":"local","command":["codegraph","serve","--mcp"],"enabled":true}}}`
- Keep `codegraph sync .` current after big moves.
