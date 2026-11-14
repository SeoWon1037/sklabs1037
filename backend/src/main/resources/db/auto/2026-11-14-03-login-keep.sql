-- ============================================
-- 🔑 로그인 유지 (서버가 다시 켜져도 · 30분이 지나도 로그인이 풀리지 않게)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - 로그인하면 기기마다 긴 무작위 표(쿠키 SKL_KEEP)를 주고, 여기에는 그 표의 SHA-256 값만 저장 (DB가 새도 표를 알 수 없음)
-- - 세션이 없을 때 이 표가 맞으면 다시 로그인 상태로 만들어 줌. 로그아웃 · 탈퇴 · 비밀번호 변경 때 지움
-- ============================================
CREATE TABLE IF NOT EXISTS T_LOGIN_KEEP (
     TOKEN_HASH     CHAR(64)        PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL
    ,EXPIRES_AT     TIMESTAMP       NOT NULL
    ,CREATED_AT     TIMESTAMP       NOT NULL DEFAULT NOW()
    ,LAST_USED_AT   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS IX_LOGIN_KEEP_USER ON T_LOGIN_KEEP (USER_ID);

CREATE INDEX IF NOT EXISTS IX_LOGIN_KEEP_EXPIRES ON T_LOGIN_KEEP (EXPIRES_AT);
