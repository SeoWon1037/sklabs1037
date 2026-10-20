-- ============================================
-- 🤖 AI 주간 코치 / 📸 성적 공유 카드 / ⚔️ 친구 대결
-- (activity.sql 다음에 실행)
-- ============================================

-- 🤖 AI 주간 코치: 사람마다 한 주에 하나 (WEEK_START = 미션을 하는 주의 월요일)
CREATE TABLE T_WEEKLY_COACH (
     USER_ID        BIGINT          NOT NULL
    ,WEEK_START     DATE            NOT NULL
    ,STATS_JSON     TEXT            NOT NULL                         -- AI가 본 지난주 숫자
    ,REPORT_JSON    TEXT            NOT NULL                         -- 한 줄 제목·돌아보기·잘한 점·팁·미션 3개
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, WEEK_START)
);

-- 📸 성적 공유 카드: 만든 순간의 숫자를 저장 (링크를 연 사람은 이 스냅샷만 봄)
CREATE TABLE T_SHARE_CARD (
     CODE           VARCHAR(16)     PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL
    ,DATA_JSON      TEXT            NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_SHARE_CARD_USER ON T_SHARE_CARD (USER_ID, INS_DAY DESC);

-- ⚔️ 친구 대결: 링크로 초대 → 받은 날부터 7일, 예측 적중·퀴즈 정답·출석 점수
CREATE TABLE T_DUEL (
     ID             BIGSERIAL       PRIMARY KEY
    ,CODE           VARCHAR(16)     NOT NULL UNIQUE
    ,HOST_ID        BIGINT          NOT NULL
    ,GUEST_ID       BIGINT
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'WAITING'       -- WAITING / ACTIVE / DONE / EXPIRED
    ,START_DATE     DATE
    ,END_DATE       DATE
    ,HOST_SCORE     INT
    ,GUEST_SCORE    INT
    ,WINNER_ID      BIGINT                                           -- 비기면 NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_DUEL_HOST ON T_DUEL (HOST_ID, ID DESC);
CREATE INDEX IX_DUEL_GUEST ON T_DUEL (GUEST_ID, ID DESC);
CREATE INDEX IX_DUEL_OPEN ON T_DUEL (STATUS) WHERE STATUS IN ('WAITING', 'ACTIVE');
