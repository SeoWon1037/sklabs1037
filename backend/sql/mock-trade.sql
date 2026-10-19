-- ============================================
-- 모의투자 테이블 (pgAdmin에서 한 번만 실행)
-- - 시작 자금 1,000만 원, 시장가 주문만, 수수료·세금 없음
-- - 돈이 마이너스가 되거나 보유 수량이 0 이하가 되는 일은
--   서비스 코드(계좌 행 잠금)에서 먼저 막고, DB CHECK 조건으로 한 번 더 막음
-- ============================================

-- 1. 계좌 (회원 1명 = 계좌 1개, 처음 모의투자 화면에 들어올 때 자동 생성)
CREATE TABLE T_MOCK_ACCOUNT (
     USER_ID      BIGINT          PRIMARY KEY REFERENCES T_USER (ID)
    ,CASH         NUMERIC(15, 0)  NOT NULL CHECK (CASH >= 0)       -- 현금 (원)
    ,INIT_CASH    NUMERIC(15, 0)  NOT NULL                         -- 시작 자금 (수익률 계산 기준)
    ,RESET_COUNT  INT             NOT NULL DEFAULT 0               -- 초기화 횟수
    ,RESET_DAY    TIMESTAMPTZ                                      -- 마지막 초기화 시각
    ,INS_DAY      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 2. 보유 종목 (다 팔면 행 삭제)
CREATE TABLE T_MOCK_HOLDING (
     USER_ID      BIGINT          NOT NULL REFERENCES T_MOCK_ACCOUNT (USER_ID)
    ,STOCK_ID     BIGINT          NOT NULL REFERENCES T_STOCK (ID)
    ,QUANTITY     INT             NOT NULL CHECK (QUANTITY > 0)    -- 보유 수량
    ,AVG_PRICE    NUMERIC(15, 2)  NOT NULL                         -- 평균 단가
    ,INS_DAY      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, STOCK_ID)
);

-- 3. 거래 내역 (체결된 주문만 기록)
CREATE TABLE T_MOCK_TRADE (
     ID            BIGSERIAL       PRIMARY KEY
    ,USER_ID       BIGINT          NOT NULL REFERENCES T_MOCK_ACCOUNT (USER_ID)
    ,STOCK_ID      BIGINT          NOT NULL REFERENCES T_STOCK (ID)
    ,SIDE          VARCHAR(4)      NOT NULL CHECK (SIDE IN ('BUY', 'SELL'))
    ,QUANTITY      INT             NOT NULL CHECK (QUANTITY > 0)
    ,PRICE         NUMERIC(15, 0)  NOT NULL                        -- 체결 가격
    ,AMOUNT        NUMERIC(15, 0)  NOT NULL                        -- 체결 금액 (가격 × 수량)
    ,PROFIT        NUMERIC(15, 0)                                  -- 매도 실현손익 (매수는 NULL)
    ,PRICE_SOURCE  VARCHAR(10)     NOT NULL                        -- LIVE: 토스 현재가 / CLOSE: DB 최근 종가
    ,INS_DAY       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 내 거래 내역 최신순 조회용
CREATE INDEX IX_MOCK_TRADE_USER ON T_MOCK_TRADE (USER_ID, ID DESC);
