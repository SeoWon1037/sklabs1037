-- ============================================
-- 💡 기능 제안 + 투표 / 🩺 포트폴리오 AI 진단 / 🧾 종목 한 장 요약
-- (pgAdmin에서 한 번만 실행, 배포 전에)
-- ※ 💱 오늘의 환율은 테이블 없음 (외부 API + 30분 캐시)
-- ============================================

-- 💡 기능 제안
CREATE TABLE T_IDEA (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,TITLE          VARCHAR(60)     NOT NULL
    ,BODY           VARCHAR(500)
    ,CATEGORY       VARCHAR(20)     NOT NULL DEFAULT '기타'
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'OPEN' CHECK (STATUS IN ('OPEN', 'PLANNED', 'DONE', 'REJECTED'))   -- 제안 / 만드는 중 / 나왔어요 / 보류
    ,ADMIN_NOTE     VARCHAR(300)                                     -- 관리자 한마디
    ,VOTE_COUNT     INT             NOT NULL DEFAULT 0
    ,DEL_DAY        TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_IDEA_LIST ON T_IDEA (VOTE_COUNT DESC, ID DESC) WHERE DEL_DAY IS NULL;

-- 💡 투표 (한 사람이 한 제안에 한 번, 다시 누르면 취소)
CREATE TABLE T_IDEA_VOTE (
     IDEA_ID        BIGINT          NOT NULL REFERENCES T_IDEA (ID)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (IDEA_ID, USER_ID)
);

-- 🩺 포트폴리오 AI 진단 기록 (하루 3번까지, 마지막 결과를 다시 보여줌)
CREATE TABLE T_MOCK_DIAGNOSIS (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,STATS_JSON     TEXT            NOT NULL                         -- 서버가 계산한 숫자
    ,RESULT_JSON    TEXT            NOT NULL                         -- AI 관찰 · 습관
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IX_MOCK_DIAGNOSIS_USER ON T_MOCK_DIAGNOSIS (USER_ID, ID DESC);

-- 🧾 종목 한 장 요약 (종목마다 마지막 1개, 3시간 지나면 새로)
CREATE TABLE T_STOCK_BRIEF (
     STOCK_ID       BIGINT          PRIMARY KEY REFERENCES T_STOCK (ID)
    ,BRIEF_JSON     TEXT            NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
