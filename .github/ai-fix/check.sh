#!/usr/bin/env bash
# ============================================
# AI가 바꾼 파일 검사 (ai-fix.yml)
# - 손대면 안 되는 곳(배포 설정·SQL·로그인·관리자·라이브러리 목록 등)을 바꿨으면 실패
# - 너무 많이 바꿨으면 실패 (작은 수정만: 파일 8개 · 400줄)
# - 비밀 값(AI 키·QA 토큰)이 바뀐 내용에 들어갔으면 실패 (사용자 글로 AI를 속여 키를 코드에 쓰게 하는 공격 대비)
# ============================================
set -euo pipefail

git add -A
files=$(git diff --cached --name-only)
[ -z "$files" ] && { echo "바뀐 파일 없음"; exit 0; }

echo "바뀐 파일:"; echo "$files"
echo "$files" > /tmp/aifix/changed.txt

blocked='^\.github/|^backend/sql/|^backend/build\.gradle|^backend/settings\.gradle|^backend/gradle|^frontend/package(-lock)?\.json|^frontend/nginx\.conf|^frontend/vite\.config|^frontend/public/sw\.js|^docker-compose|^\.env|^qa/|^backend/src/main/resources/application|/sklabs1037/(user|admin|push|support|autofix|ops|client)/|(Login|OAuth|Oauth|Security|Auth)[A-Za-z]*\.(java|jsx?|xml)$'
bad=$(echo "$files" | grep -E "$blocked" || true)
if [ -n "$bad" ]; then
  echo "::error::AI가 고치면 안 되는 파일을 바꿨어요: $bad"
  exit 1
fi

count=$(echo "$files" | wc -l)
lines=$(git diff --cached --numstat | awk '{s+=$1+$2} END {print s+0}')
if [ "$count" -gt 8 ] || [ "$lines" -gt 400 ]; then
  echo "::error::수정이 너무 커요 (파일 $count개, $lines줄). 작은 수정만 자동으로 해요."
  exit 1
fi

for secret in "${SECRET_1:-}" "${SECRET_2:-}"; do
  if [ -n "$secret" ] && git diff --cached | grep -qF "$secret"; then
    echo "::error::바뀐 내용에 비밀 값이 들어 있어요."
    exit 1
  fi
done

echo "검사 통과 (파일 $count개, $lines줄)"
