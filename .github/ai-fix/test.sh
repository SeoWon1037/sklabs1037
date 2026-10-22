#!/usr/bin/env bash
# AI 수정 뒤 확인: 백엔드 빌드·테스트 + 프론트 검사·빌드 (배포 CI와 같은 명령)
set -uo pipefail
log=/tmp/aifix/test.log
: > "$log"
( cd backend && ./gradlew build --console=plain ) >> "$log" 2>&1 || { echo "백엔드 빌드·테스트 실패" >> "$log"; exit 1; }
( cd frontend && npm run lint && npm run build ) >> "$log" 2>&1 || { echo "프론트 검사·빌드 실패" >> "$log"; exit 1; }
echo "모두 통과" >> "$log"
