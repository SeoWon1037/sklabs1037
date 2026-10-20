-- ============================================
-- 🤖 AI vs 사람 투자 리그 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 성격이 다른 AI 투자자 3명이 평일 09:10에 직접 종목을 골라 모의투자에 참여
-- - 사람과 같은 랭킹에서 경쟁, 매매 일지(왜 샀는지)를 /league 에 공개
-- 1. AI 회원 3명 (T_USER) : 비밀번호 없음 + PROVIDER='AI' + 받을 수 없는 메일 주소(.invalid) → 로그인·비밀번호 찾기 불가
-- 2. T_AI_TRADER    : 어떤 회원이 AI인지 + 성격 코드 (투자 규칙은 서버 코드 AiTraderStyle)
-- 3. T_AI_TRADE_LOG : 하루 1번 매매 일지 (시장 판단, 일지, 주문 결과)
-- ============================================

INSERT INTO T_USER (EMAIL, NICKNAME, ROLE, PROVIDER, PROVIDER_ID, INS_DAY) VALUES
 ('ai-news@sklabs.invalid',     'AI 뉴스헌터', 'USER', 'AI', 'NEWS',     NOW())
,('ai-momentum@sklabs.invalid', 'AI 추세추종', 'USER', 'AI', 'MOMENTUM', NOW())
,('ai-steady@sklabs.invalid',   'AI 안정분산', 'USER', 'AI', 'STEADY',   NOW());

CREATE TABLE T_AI_TRADER (
     USER_ID        BIGINT          PRIMARY KEY REFERENCES T_USER (ID)
    ,CODE           VARCHAR(20)     NOT NULL UNIQUE      -- NEWS / MOMENTUM / STEADY
    ,ACTIVE_YN      CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (ACTIVE_YN IN ('Y', 'N'))
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO T_AI_TRADER (USER_ID, CODE)
SELECT  ID, PROVIDER_ID
  FROM T_USER
 WHERE PROVIDER = 'AI';

CREATE TABLE T_AI_TRADE_LOG (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_AI_TRADER (USER_ID)
    ,TRADE_DATE     DATE            NOT NULL
    ,STATUS         VARCHAR(10)     NOT NULL CHECK (STATUS IN ('DONE', 'HOLD', 'FAIL'))   -- 매매함 / 안 사고팔기로 함 / 실패
    ,MARKET_VIEW    VARCHAR(300)                         -- 오늘 시장 판단 한 줄
    ,DIARY          VARCHAR(1000)                        -- 매매 일지 (AI가 쓴 글)
    ,ORDERS_JSON    TEXT                                 -- 주문 결과 [{symbol, name, side, quantity, price, reason, status, note}]
    ,TOTAL_ASSET    NUMERIC(15, 0)                       -- 매매 전 총자산
    ,MESSAGE        VARCHAR(500)                         -- 실패 이유
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UNIQUE (USER_ID, TRADE_DATE)                        -- 하루 한 번
);

CREATE INDEX IX_AI_TRADE_LOG_DATE ON T_AI_TRADE_LOG (TRADE_DATE DESC, ID DESC);
