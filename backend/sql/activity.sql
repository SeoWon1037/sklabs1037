-- ============================================
-- 🏅 업적·배지 / 🔔 알림 센터 / 📅 출석 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- 1. T_USER_BADGE    : 받은 배지 (조건은 서버 코드 BadgeService, 받은 날짜만 기록)
-- 2. T_NOTIFICATION  : 사이트 안 알림 (배지, 예측 결과, 내 글 댓글, 목표가 도달, 신고로 가려짐)
--                      DEDUPE_KEY: 같은 알림 두 번 안 가게 (예: PREDICT:2026-10-21)
-- 3. T_ATTENDANCE    : 출석 (로그인하고 메인을 열면 하루 한 번)
-- ============================================
CREATE TABLE T_USER_BADGE (
     USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,CODE           VARCHAR(30)     NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, CODE)
);

CREATE TABLE T_NOTIFICATION (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,KIND           VARCHAR(20)     NOT NULL      -- BADGE / PREDICT / COMMENT / ALERT / REPORT
    ,TITLE          VARCHAR(120)    NOT NULL
    ,BODY           VARCHAR(300)
    ,LINK           VARCHAR(200)                  -- 누르면 갈 화면
    ,DEDUPE_KEY     VARCHAR(80)
    ,READ_YN        CHAR(1)         NOT NULL DEFAULT 'N' CHECK (READ_YN IN ('Y', 'N'))
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IX_NOTIFICATION_USER ON T_NOTIFICATION (USER_ID, ID DESC);
CREATE UNIQUE INDEX UX_NOTIFICATION_DEDUPE ON T_NOTIFICATION (USER_ID, DEDUPE_KEY) WHERE DEDUPE_KEY IS NOT NULL;

CREATE TABLE T_ATTENDANCE (
     USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,DAY            DATE            NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, DAY)
);
