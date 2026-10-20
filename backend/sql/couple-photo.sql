-- ============================================
-- 📸 커플 기념일 앨범 (couple.sql 다음에, pgAdmin에서 한 번만 실행)
-- - 100일 · 200일 · 1주년 같은 지난 기념일(또는 "추억")마다 사진
-- - 사진은 화면에서 줄여서(긴 변 1600px JPEG) DB에 저장 → 두 사람만 볼 수 있음
-- ============================================
CREATE TABLE T_COUPLE_PHOTO (
     ID             BIGSERIAL       PRIMARY KEY
    ,COUPLE_ID      BIGINT          NOT NULL REFERENCES T_COUPLE (ID)
    ,UPLOADER_ID    BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,MILESTONE      VARCHAR(20)     NOT NULL                         -- 100일 / 1주년 / 추억
    ,CAPTION        VARCHAR(100)
    ,CONTENT_TYPE   VARCHAR(20)     NOT NULL
    ,BYTES          INT             NOT NULL
    ,DATA           BYTEA           NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_COUPLE_PHOTO ON T_COUPLE_PHOTO (COUPLE_ID, ID);
