#!/bin/bash
# Dispatch (or resume) an opencode worker on a task brief. State lives in the repo:
#   docs/cross-platform/orchestration/runs.tsv  append-only: start / end / resume / fallback events with opencode session ids
#   docs/cross-platform/tasks/<TASK>.md         the brief; workers append to its "Worker log" section
#   .orchestration/logs/                         raw JSON event logs (untracked, disposable)
# Usage: tools/orchestrate/dispatch.sh <TASK-ID> <provider/model | pi:provider/model> [--resume <session-id>]
# A `pi:` prefix runs the worker with the pi CLI instead of opencode (needs the untracked .pi/mcp.json for codegraph).
# If a free model fails for credits/quota/rate-limit reasons, the task is re-run once on $FALLBACK_MODEL
# (user instructions 2026-11-05: OpenRouter DeepSeek V4 Flash, run through pi; still reviewed by the orchestrator).
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1
TASK="$1"; MODEL="$2"; RESUME="${4:-}"
FALLBACK_MODEL="${FALLBACK_MODEL:-pi:openrouter/deepseek/deepseek-v4-flash}"
BRIEF="docs/cross-platform/tasks/$TASK.md"
[ -f "$BRIEF" ] || { echo "no brief $BRIEF"; exit 2; }
RUNS=docs/cross-platform/orchestration/runs.tsv
mkdir -p .orchestration/logs .orchestration/tmp
ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }

PROMPT="You are a worker implementing ONE task in this repository (a Kotlin Multiplatform migration of the Deepr Android app).
Read $BRIEF completely, then docs/cross-platform/orchestration/WORKER_RULES.md, and follow both exactly.
Use codegraph (the codegraph MCP tools, or the \`codegraph\` CLI) to locate and read code (and to check callers/impact) before editing; prefer it over reading whole files.
When finished, append a short entry to the 'Worker log' section of $BRIEF (what you changed, the verification commands you ran and their result, anything left undone), then reply with the same summary."

# run <model> [session]; sets LOG, EXIT, SESSION
run() {
  local model="$1" session="${2:-}"
  LOG=".orchestration/logs/$TASK-${model//[\/:]/_}-$(date +%Y%m%d-%H%M%S).jsonl"
  if [[ "$model" == pi:* ]]; then
    local resume_args=() msg="$PROMPT" event=start
    if [ -n "$session" ]; then
      resume_args=(--session "$session"); event=resume
      msg="You were interrupted. Re-read $BRIEF (including the Worker log) and the current git diff, then continue the task from where it stopped."
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(ts)" "$TASK" "$model" "$event" "$session" "$LOG" >> "$RUNS"
    timeout "${WORKER_TIMEOUT:-3600}" pi --print --mode json --approve --model "${model#pi:}" --name "$TASK" "${resume_args[@]}" "$msg" > "$LOG" 2>&1
  elif [ -n "$session" ]; then
    printf '%s\t%s\t%s\tresume\t%s\t%s\n' "$(ts)" "$TASK" "$model" "$session" "$LOG" >> "$RUNS"
    timeout "${WORKER_TIMEOUT:-3600}" opencode run --standalone --format json -m "$model" -s "$session" \
      "You were interrupted. Re-read $BRIEF (including the Worker log) and the current git diff, then continue the task from where it stopped." > "$LOG" 2>&1
  else
    printf '%s\t%s\t%s\tstart\t\t%s\n' "$(ts)" "$TASK" "$model" "$LOG" >> "$RUNS"
    timeout "${WORKER_TIMEOUT:-3600}" opencode run --standalone --format json -m "$model" --title "$TASK" "$PROMPT" > "$LOG" 2>&1
  fi
  EXIT=$?
  SESSION=$(grep -o '"sessionID":"[^"]*"' "$LOG" | head -1 | cut -d'"' -f4)
  [ -n "$SESSION" ] || SESSION=$(head -1 "$LOG" | grep -o '"type":"session".*"id":"[^"]*"' | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  printf '%s\t%s\t%s\tend\t%s\texit=%s log=%s\n' "$(ts)" "$TASK" "$model" "$SESSION" "$EXIT" "$LOG" >> "$RUNS"
}

run "$MODEL" "$RESUME"
# Out of free credits / quota / rate limited → fall back once (fresh session; partial work stays on disk for it).
if [[ "$MODEL" == *-free ]] && grep -qiE 'credit|quota|insufficient|rate.?limit|too many requests|"(status|code)":(402|429)|payment required|usage limit' "$LOG" \
   && { [ "$EXIT" -ne 0 ] || grep -q '"type":"error"' "$LOG"; }; then
  printf '%s\t%s\t%s\tfallback\t\tfrom=%s\n' "$(ts)" "$TASK" "$FALLBACK_MODEL" "$MODEL" >> "$RUNS"
  MODEL="$FALLBACK_MODEL"
  run "$MODEL"
fi
echo "task=$TASK model=$MODEL exit=$EXIT session=$SESSION log=$LOG"
