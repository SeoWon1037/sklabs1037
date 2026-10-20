-- ============================================
-- 🎯 오를까 내릴까 (매일 예측 게임) - pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!
-- - 라운드 = 거래일 하나: 그날 종가가 전 거래일 종가보다 오를지 내릴지 5종목 맞히기
-- - 투표 마감: 그 거래일 09:00 (장 시작 전) / 채점: 평일 21:10 (20:30 시세 수집 뒤)
-- - 같이 겨루는 "모멘텀 봇": 최근 5일 수익률이 +면 UP, 아니면 DOWN (규칙 하나짜리 비교 기준)
-- ============================================
CREATE TABLE T_PREDICT_ITEM (
     ID             BIGSERIAL       PRIMARY KEY
    ,TRADE_DATE     DATE            NOT NULL                         -- 맞힐 거래일
    ,STOCK_ID       BIGINT          NOT NULL REFERENCES T_STOCK (ID)
    ,SEQ            INT             NOT NULL                         -- 화면 순서
    ,BOT_PICK       VARCHAR(4)      CHECK (BOT_PICK IN ('UP', 'DOWN'))
    ,BASE_DATE      DATE                                             -- 비교 기준 거래일 (채점 때)
    ,BASE_CLOSE     NUMERIC(15, 2)
    ,RESULT_CLOSE   NUMERIC(15, 2)
    ,RESULT         VARCHAR(4)      CHECK (RESULT IN ('UP', 'DOWN', 'FLAT', 'VOID'))   -- VOID: 휴장 등으로 결과 없음
    ,GRADED_DAY     TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UNIQUE (TRADE_DATE, STOCK_ID)
);

CREATE TABLE T_PREDICT_VOTE (
     ITEM_ID        BIGINT          NOT NULL REFERENCES T_PREDICT_ITEM (ID)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,PICK           VARCHAR(4)      NOT NULL CHECK (PICK IN ('UP', 'DOWN'))
    ,CORRECT        BOOLEAN                                          -- 채점 전·보합·무효는 NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (ITEM_ID, USER_ID)
);

CREATE INDEX IX_PREDICT_VOTE_USER ON T_PREDICT_VOTE (USER_ID);
CREATE INDEX IX_PREDICT_ITEM_OPEN ON T_PREDICT_ITEM (TRADE_DATE) WHERE RESULT IS NULL;
