#!/bin/sh
# ============================================
# 💾 DB 백업 컨테이너 (docker-compose의 backup 서비스가 실행)
# - 매일 04:30 (한국 시간 = 19:30 UTC) RDS를 pg_dump로 통째로 떠서 ~/sklabs1037/backups/ 에 저장
# - 최근 7개만 남김 · 결과는 last.json (관리자 > 🖥 서버 상태 > 💾 DB 백업에서 보임)
# - 관리자 화면 [지금 백업] → 서버가 /backups/run-now 파일을 만들면 30초 안에 시작
# - 켜질 때 20시간 안에 만든 백업이 없으면 바로 한 번
# - 접속 정보는 .env 의 DB_URL · DB_USERNAME · DB_PASSWORD (따로 넣을 것 없음)
# 복구 예: docker run --rm -it -v ~/sklabs1037/backups:/b postgres:18-alpine \
#           pg_restore -h <RDS 주소> -U <사용자> -d <새 DB> --no-owner /b/sklabs-YYYYMMDD-HHMM.dump
# ============================================
set -u
KEEP=7

# jdbc:postgresql://host:5432/db?ssl=... → host / port / db
REST=${DB_URL#jdbc:postgresql://}
HOSTPORT=${REST%%/*}
DBQ=${REST#*/}
DB=${DBQ%%\?*}
HOST=${HOSTPORT%%:*}
PORT=${HOSTPORT##*:}
[ "$PORT" = "$HOSTPORT" ] && PORT=5432

backup() {
  ts=$(date -u +%Y%m%d-%H%M)
  f="/backups/sklabs-$ts.dump"
  start=$(date +%s)
  echo "[$(date -u)] 백업 시작 → $f"
  if PGPASSWORD="$DB_PASSWORD" pg_dump -h "$HOST" -p "$PORT" -U "$DB_USERNAME" -d "$DB" -Fc -Z 6 --no-owner -f "$f.part" 2>/backups/last-error.txt; then
    mv "$f.part" "$f"
    rm -f /backups/last-error.txt
    printf '{"ok":true,"file":"%s","at":%s,"seconds":%s}\n' "$(basename "$f")" "$(date +%s)" "$(( $(date +%s) - start ))" > /backups/last.json
    echo "[$(date -u)] 백업 끝 ($(( $(date +%s) - start ))초)"
  else
    rm -f "$f.part"
    printf '{"ok":false,"at":%s,"seconds":%s}\n' "$(date +%s)" "$(( $(date +%s) - start ))" > /backups/last.json
    echo "[$(date -u)] 백업 실패: $(head -c 300 /backups/last-error.txt)"
  fi
  # 오래된 것 지우기 (최근 KEEP개만)
  ls -1t /backups/sklabs-*.dump 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f
}

mkdir -p /backups
[ -z "$(find /backups -name 'sklabs-*.dump' -mmin -1200 2>/dev/null)" ] && backup

while true; do
  if [ "$(date -u +%H%M)" = "1930" ]; then backup; sleep 61; fi
  if [ -f /backups/run-now ]; then rm -f /backups/run-now; backup; fi
  sleep 30
done
