-- ============================================
-- 🎂 커플 생일 (커플 화면에서 두 사람 생일을 입력 → D-day, 7일 전 · 하루 전 · 당일 알림)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - 커플 한 쌍에 사람마다 한 줄. 월 · 일만 저장 (태어난 해는 받지 않음, 양력 기준)
-- - 두 사람 모두 서로의 생일을 넣고 고칠 수 있음
-- ============================================
CREATE TABLE IF NOT EXISTS T_COUPLE_BIRTHDAY (
     COUPLE_ID      BIGINT          NOT NULL
    ,USER_ID        BIGINT          NOT NULL
    ,BIRTH_MONTH    SMALLINT        NOT NULL
    ,BIRTH_DAY      SMALLINT        NOT NULL
    ,UPD_BY         BIGINT
    ,UPD_DAY        TIMESTAMP       NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (COUPLE_ID, USER_ID)
);

CREATE INDEX IF NOT EXISTS IX_COUPLE_BIRTHDAY_DAY ON T_COUPLE_BIRTHDAY (BIRTH_MONTH, BIRTH_DAY);
