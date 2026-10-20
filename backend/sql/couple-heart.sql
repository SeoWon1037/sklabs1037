-- ============================================
-- 💗 커플 하트 날리기 (couple.sql 다음에, pgAdmin에서 한 번만 실행)
-- - 커플끼리만, 누를 때마다 1줄 → 보낸·받은 하트 수, 오늘 몇 번째
-- ============================================
CREATE TABLE T_COUPLE_HEART (
     ID             BIGSERIAL       PRIMARY KEY
    ,COUPLE_ID      BIGINT          NOT NULL REFERENCES T_COUPLE (ID)
    ,FROM_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_COUPLE_HEART ON T_COUPLE_HEART (COUPLE_ID, FROM_ID, INS_DAY DESC);
