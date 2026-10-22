#!/bin/bash
# Dispatch (or resume) an opencode worker on a task brief. State lives in the repo:
#   docs/cross-platform/orchestration/runs.tsv  append-only: start / end / resume events with opencode session ids
#   docs/cross-platform/tasks/<TASK>.md         the brief; workers append to its "Worker log" section
#   .orchestration/logs/                         raw JSON event logs (untracked, disposable)
# Usage: tools/orchestrate/dispatch.sh <TASK-ID> <provider/model> [--resume <session-id>]
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1
TASK="$1"; MODEL="$2"; RESUME="${4:-}"
BRIEF="docs/cross-platform/tasks/$TASK.md"
[ -f "$BRIEF" ] || { echo "no brief $BRIEF"; exit 2; }
RUNS=docs/cross-platform/orchestration/runs.tsv
mkdir -p .orchestration/logs
LOG=".orchestration/logs/$TASK-${MODEL//\//_}-$(date +%Y%m%d-%H%M%S).jsonl"
ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }

PROMPT="You are a worker implementing ONE task in this repository (a Kotlin Multiplatform migration of the Deepr Android app).
Read $BRIEF completely, then docs/cross-platform/orchestration/WORKER_RULES.md, and follow both exactly.
Use the codegraph_explore MCP tool to locate and read code (and to check callers/impact) before editing; prefer it over reading whole files.
When finished, append a short entry to the 'Worker log' section of $BRIEF (what you changed, the verification commands you ran and their result, anything left undone), then reply with the same summary."

if [ -n "$RESUME" ]; then
  printf '%s\t%s\t%s\tresume\t%s\t\n' "$(ts)" "$TASK" "$MODEL" "$RESUME" >> "$RUNS"
  opencode run --pure --format json -m "$MODEL" -s "$RESUME" "You were interrupted. Re-read $BRIEF (including the Worker log) and the current git diff, then continue the task from where it stopped." > "$LOG" 2>&1
else
  printf '%s\t%s\t%s\tstart\t\t%s\n' "$(ts)" "$TASK" "$MODEL" "$LOG" >> "$RUNS"
  timeout "${WORKER_TIMEOUT:-3600}" opencode run --pure --format json -m "$MODEL" --title "$TASK" "$PROMPT" > "$LOG" 2>&1
fi
EXIT=$?
SESSION=$(grep -o '"sessionID":"[^"]*"' "$LOG" | head -1 | cut -d'"' -f4)
printf '%s\t%s\t%s\tend\t%s\texit=%s log=%s\n' "$(ts)" "$TASK" "$MODEL" "$SESSION" "$EXIT" "$LOG" >> "$RUNS"
echo "task=$TASK model=$MODEL exit=$EXIT session=$SESSION log=$LOG"
