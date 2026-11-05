# M2+ orchestration: Claude orchestrates & verifies, opencode free models implement

User instruction (2026-10-22): delegate implementation to opencode free models chosen by quality/capability; the orchestrator (Claude) plans, reviews, verifies and commits. Use codegraph. State must survive interruptions of workers and of the orchestrator.

## Resume checkpoint (2026-11-05, harness restart)
- In flight at restart: **M2-11** worker (`dispatch.sh M2-11 opencode/deepseek-v4.1-flash-free`, free tier exhausted, so it falls back to `openrouter/deepseek/deepseek-v4-flash`). If its `runs.tsv` rows show a `start` without a matching `end`, it was killed: check `git status` for partial work, then resume with `tools/orchestrate/dispatch.sh M2-11 openrouter/deepseek/deepseek-v4-flash --resume <sessionID from its log>` or finish/review by hand.
- CI to check: runs 42064628205 (Android) / 42064628200 (iOS, first run on `macos-15`) cover M2-08b..M2-10b. When green, close issues #1, #2, #3 and update #4 and epic #16.
- Next after M2-11: M2-12 AddLinkScreen (#6), M2-13 Home + Splash/Dashboard2 (#7), M2-14 Settings (#8), M2-15 thin host (#9).

## GitHub issues (user instruction 2026-11-05)
Plans and work are also tracked as issues in this repo (`sillygoose-armando/Deepr`), titled `RnD: p2p-bmarks: …`. The epic is **#16** (pinned); one issue per open task or milestone (#1–#15). On task completion, comment the commit + CI run on its issue, close it, and tick it in #16. New tasks get a new issue. QUEUE.md stays the detailed source of truth; issues are the shared view.

## Models
Free opencode models first (see MODELS.md). When they run out of credits/quota, `dispatch.sh` automatically re-runs the task on **`openrouter/deepseek/deepseek-v4-flash`** (logged as `fallback` in runs.tsv); review is unchanged.

## Credits
- 2026-11-05: both exhausted. opencode free tier: `provider.free-limit` 429 ("Free usage limit reached"). OpenRouter: the key's monthly limit; opencode asks for up to 256k output tokens per request, which fails (402) once the remaining budget is smaller, and later requests hang while opencode retries. To resume: wait for the free limit to reset, or raise the OpenRouter key limit. The user added OpenRouter credits (2026-11-05). The worktree `opencode.json` now caps OpenRouter deepseek-v4-flash output at 32k tokens (`provider.openrouter.models["deepseek/deepseek-v4-flash"].limit = {context: 256000, output: 32000}`), so requests stay affordable. Formerly suggested: cap output tokens in the worktree `opencode.json` (`provider.openrouter.models["deepseek/deepseek-v4-flash"].limit.output`), untested while out of credit.

## opencode background service
opencode v2 routes `opencode run` through a shared background server (`opencode serve --service`), which also serves other projects on this machine. On 2026-11-05 it stopped processing requests (runs hung with no output). `dispatch.sh` therefore uses **`opencode run --standalone`** (a private server per run) and never touches the shared service.

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
- opencode v2.x (upgraded by 2026-11-05) removed `--pure`, and may return exit 1 even when the model finished normally (check the log's last `step_finish` `reason`); headless `opencode run` works without it now. Workers use codegraph MCP from the worktree's untracked `opencode.json`. If that file is missing, recreate it:
  `{"$schema":"https://opencode.ai/config.json","permission":{"external_directory":"allow"},"mcp":{"codegraph":{"type":"local","command":["codegraph","serve","--mcp"],"enabled":true}}}`
  `external_directory: allow` matters: headless `opencode run` **ends the session** when it auto-rejects a path outside the repo (e.g. a worker reading a library jar in `~/.gradle`). That killed M2-02 attempts 1–3.
- Keep `codegraph sync .` current after big moves.
