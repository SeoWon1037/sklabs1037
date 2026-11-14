-- ============================================
-- 📷 약속 사진 (약속마다 4장까지 — 가기로 한 곳, 다녀온 사진)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - 볼 수 있는 사람: 약속을 만든 사람 + (함께하는 약속이면) 커플인 상대
-- - 약속을 지우면 사진도 같이 지움 (약속이 없는 사진은 매일 09:00 정리)
-- ============================================
CREATE TABLE IF NOT EXISTS T_PLAN_PHOTO (
     ID             BIGSERIAL       PRIMARY KEY
    ,PLAN_ID        BIGINT          NOT NULL
    ,UPLOADER_ID    BIGINT          NOT NULL
    ,CONTENT_TYPE   VARCHAR(20)     NOT NULL
    ,BYTES          INT             NOT NULL
    ,DATA           BYTEA           NOT NULL
    ,INS_DAY        TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_PLAN_PHOTO_PLAN ON T_PLAN_PHOTO (PLAN_ID, ID);
