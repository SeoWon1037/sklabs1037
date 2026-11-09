#!/usr/bin/env bash
# ============================================
# ⏪ 바로 전 버전으로 되돌리기 (EC2에서 실행)
# - deploy.yml이 부름: 배포 후 서버가 3분 안에 안 뜨거나(HEALTH), QA 봇이 새 심각한 문제를 찾았을 때(QA)
# - 배포 전에 붙여 둔 :prev 이미지를 다시 :latest 로 → 빌드 없이 컨테이너만 교체 (1분 안쪽)
# - DB는 건드리지 않음 (SQL은 항상 "추가만" 하고 push 전에 실행하므로 옛 코드도 그대로 돌아감)
# - 되돌린 뒤 서버에 알려서 관리자 메일 + 📲
# 사용: bash .github/deploy/rollback.sh HEALTH|QA <commit> "<이유>"
# ============================================
set -u
cd ~/sklabs1037

KIND="${1:-QA}"
COMMIT="${2:-}"
REASON="${3:-}"
IMAGES="ghcr.io/seowon1037/sklabs1037-backend ghcr.io/seowon1037/sklabs1037-frontend"

for img in $IMAGES; do
  if ! docker image inspect "$img:prev" >/dev/null 2>&1; then
    echo "❌ $img:prev 가 없어서 되돌릴 수 없어요 (처음 배포이거나 이미지가 지워짐)"
    exit 1
  fi
done

echo "⏪ 바로 전 버전으로 되돌려요 ($KIND)"
for img in $IMAGES; do docker tag "$img:prev" "$img:latest"; done
docker compose up -d --no-build

# 옛 버전이 다시 뜰 때까지 (최대 3분)
for n in $(seq 1 36); do
  code=$(curl -sk -o /dev/null -w '%{http_code}' --resolve sklabs1037.com:443:127.0.0.1 https://sklabs1037.com/api/health || true)
  [ "$code" = "200" ] && break
  sleep 5
done
echo "되돌린 서버 상태: $code"

# 서버에 알리기 (관리자 메일 + 📲). 따옴표·역슬래시는 빼서 JSON이 깨지지 않게
TOKEN=$(grep -E '^QA_TOKEN=' .env | head -1 | cut -d= -f2- | tr -d '"'"'"'\r')
if [ -n "$TOKEN" ]; then
  SAFE=$(printf '%s' "$REASON" | tr -d '"\\\r\n' | cut -c1-400)
  curl -sk --max-time 10 --resolve sklabs1037.com:443:127.0.0.1 -X POST https://sklabs1037.com/api/qa/rolled-back \
    -H 'Content-Type: application/json' -H "X-QA-Token: $TOKEN" \
    -d "{\"kind\":\"$KIND\",\"commit\":\"$COMMIT\",\"reason\":\"$SAFE\"}" || true
  echo
fi

docker compose ps
