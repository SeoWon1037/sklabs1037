#!/usr/bin/env bash
# 서버에 진행 상황 알리기: callback.sh <STATUS> [pr] [branch] [summary파일] [error]
# - 서버 POST /api/autofix/callback (헤더 X-QA-Token) → 관리자 🔔·📲
set -uo pipefail
summary=""
[ -n "${4:-}" ] && [ -f "$4" ] && summary=$(head -c 1800 "$4")
files=""
[ -f /tmp/aifix/changed.txt ] && files=$(head -40 /tmp/aifix/changed.txt)
jq -n --arg issue "$ISSUE" --arg status "$1" --arg pr "${2:-}" --arg branch "${3:-}" \
      --arg summary "$summary" --arg files "$files" --arg error "${5:-}" --arg runUrl "$RUN_URL" \
      '{issue:$issue, status:$status, pr:$pr, branch:$branch, summary:$summary, files:$files, error:$error, runUrl:$runUrl}' \
  | curl -sS -m 20 -X POST "https://sklabs1037.com/api/autofix/callback" \
      -H "Content-Type: application/json" -H "X-QA-Token: $QA_TOKEN" --data-binary @- || true
echo
