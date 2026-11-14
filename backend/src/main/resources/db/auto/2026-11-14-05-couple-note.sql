-- ============================================
-- 💌 커플 한마디 (커플 화면에서 짧은 메시지를 쓰면 상대에게 알림 · 푸시로 내용이 감)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - 100자까지, 커플 화면에는 최근 10개가 보임 (두 사람에게만)
-- ============================================
CREATE TABLE IF NOT EXISTS T_COUPLE_NOTE (
     ID             BIGSERIAL       PRIMARY KEY
    ,COUPLE_ID      BIGINT          NOT NULL
    ,FROM_ID        BIGINT          NOT NULL
    ,CONTENT        VARCHAR(100)    NOT NULL
    ,INS_DAY        TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_COUPLE_NOTE_COUPLE ON T_COUPLE_NOTE (COUPLE_ID, ID);
