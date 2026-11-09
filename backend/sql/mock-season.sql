-- ============================================
-- 🏆 모의투자 주간 시즌 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 매주 토요일 09:00 그 주(월~금) 수익률 TOP 3(사람만, AI 리그 제외)를 기록 → 알림 + 배지(👑 주간 챔피언 / 🏅 주간 TOP 3)
-- - 모의투자 > 수익률 랭킹 > [🏆 지난 시즌] 에서 최근 8주 명예의 전당
-- - 같은 주를 두 번 돌려도 한 번만 기록 (PRIMARY KEY)
-- ============================================
CREATE TABLE IF NOT EXISTS T_MOCK_SEASON (
     WEEK_START     DATE            NOT NULL                     -- 그 주 월요일
    ,RANK_NO        INTEGER         NOT NULL CHECK (RANK_NO BETWEEN 1 AND 3)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,NICKNAME       VARCHAR(50)     NOT NULL                     -- 그때 닉네임 (나중에 바꿔도 기록은 그대로)
    ,RETURN_RATE    NUMERIC(9, 2)   NOT NULL                     -- 그 주 수익률 (%)
    ,TOTAL_ASSET    NUMERIC(15, 0)  NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (WEEK_START, USER_ID)
);

CREATE INDEX IF NOT EXISTS IX_MOCK_SEASON_WEEK ON T_MOCK_SEASON (WEEK_START DESC, RANK_NO);
