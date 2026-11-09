-- ============================================
-- 📊 사용 통계 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 화면을 열 때마다 브라우저가 POST /api/track (주소 첫 칸만: /stock, /mock …) → 서버가 1분마다 모아서 저장
-- - 방문자: 브라우저마다 만든 무작위 번호를 서버에서 다시 해시해서 하루 단위로만 셈 (IP · 회원 번호 · 쿠키 추적 없음)
-- - 관리자 화면: /admin/stats (방문자 · 페이지뷰 · 인기 화면 · 가입 · 기능별 사용 · 체험 → 가입)
-- - 90일 지난 기록은 자동 삭제
-- ============================================
CREATE TABLE IF NOT EXISTS T_DAILY_STAT (
     DAY            DATE            NOT NULL
    ,METRIC         VARCHAR(20)     NOT NULL                     -- PV(화면 열기) / EVENT(체험 시작 등)
    ,KEY            VARCHAR(60)     NOT NULL                     -- /stock, demo_login ...
    ,CNT            BIGINT          NOT NULL DEFAULT 0
    ,PRIMARY KEY (DAY, METRIC, KEY)
);

CREATE TABLE IF NOT EXISTS T_DAILY_VISITOR (
     DAY            DATE            NOT NULL
    ,VID            CHAR(16)        NOT NULL                     -- 해시한 방문자 번호 (원래 번호로 되돌릴 수 없음)
    ,PRIMARY KEY (DAY, VID)
);
