-- ============================================
-- 💸 AI 비용 안전장치 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 서버가 Claude API를 부를 때마다 응답의 토큰 수(usage)로 비용을 계산해서 날짜 · 기능별로 더함
-- - 오늘 쓴 돈이 하루 한도를 넘으면 AI 기능을 자동으로 멈춤 (다음 날 0시에 다시 열림) + 80%·100%에 관리자 메일·📲
-- - 관리자 화면 /admin/ai-cost 에서 한도 바꾸기 · 지금 바로 멈추기(긴급 정지)
-- - GitHub AI 자동 수정(Claude Code)은 GitHub 쪽 키로 돌아서 여기에 안 잡힘 (그쪽은 1번에 $3 한도)
-- ============================================
CREATE TABLE IF NOT EXISTS T_AI_USAGE (
     DAY            DATE            NOT NULL                     -- 한국 날짜
    ,FEATURE        VARCHAR(40)     NOT NULL                     -- 부른 기능 (AdCopy, Inquiry, NewsSummary ...)
    ,CALLS          INTEGER         NOT NULL DEFAULT 0
    ,FAILS          INTEGER         NOT NULL DEFAULT 0
    ,IN_TOKENS      BIGINT          NOT NULL DEFAULT 0
    ,OUT_TOKENS     BIGINT          NOT NULL DEFAULT 0
    ,CACHE_READ     BIGINT          NOT NULL DEFAULT 0
    ,CACHE_WRITE    BIGINT          NOT NULL DEFAULT 0
    ,COST_USD       NUMERIC(12, 6)  NOT NULL DEFAULT 0
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (DAY, FEATURE)
);

-- 설정 (한 줄만)
CREATE TABLE IF NOT EXISTS T_AI_BUDGET (
     ID             INTEGER         PRIMARY KEY CHECK (ID = 1)
    ,DAILY_USD      NUMERIC(8, 2)   NOT NULL DEFAULT 2.00        -- 하루 한도 (달러)
    ,PAUSED_YN      CHAR(1)         NOT NULL DEFAULT 'N' CHECK (PAUSED_YN IN ('Y', 'N'))   -- 긴급 정지
    ,UPD_BY         BIGINT          REFERENCES T_USER (ID)
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO T_AI_BUDGET (ID) VALUES (1) ON CONFLICT (ID) DO NOTHING;
