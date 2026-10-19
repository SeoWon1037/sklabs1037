-- ============================================
-- 모의투자 지정가 주문 (pgAdmin에서 한 번만 실행, mock-trade.sql 다음에)
-- - 매수 주문: 주문할 때 (지정가 × 수량)만큼 현금을 미리 빼서 RESERVED_AMOUNT에 묶어둠
--             → 체결되면 실제 체결 금액과의 차이를 돌려주고, 취소·만료되면 전부 돌려줌
-- - 매도 주문: 주식은 보유 종목에 그대로 두고, "대기 중인 매도 수량"만큼 다른 매도를 막음
-- - 정규장(평일 09:00~15:30)에 1분마다 확인해서 체결, 그 거래일 15:35에 미체결은 만료
-- ============================================

-- 1. 지정가 주문
CREATE TABLE T_MOCK_ORDER (
     ID               BIGSERIAL       PRIMARY KEY
    ,USER_ID          BIGINT          NOT NULL REFERENCES T_MOCK_ACCOUNT (USER_ID)
    ,STOCK_ID         BIGINT          NOT NULL REFERENCES T_STOCK (ID)
    ,SIDE             VARCHAR(4)      NOT NULL CHECK (SIDE IN ('BUY', 'SELL'))
    ,QUANTITY         INT             NOT NULL CHECK (QUANTITY > 0)
    ,LIMIT_PRICE      NUMERIC(15, 0)  NOT NULL CHECK (LIMIT_PRICE > 0)        -- 지정가
    ,RESERVED_AMOUNT  NUMERIC(15, 0)  NOT NULL DEFAULT 0 CHECK (RESERVED_AMOUNT >= 0)  -- 매수 주문에 묶어둔 돈
    ,STATUS           VARCHAR(10)     NOT NULL DEFAULT 'PENDING'
                                      CHECK (STATUS IN ('PENDING', 'FILLED', 'CANCELED', 'EXPIRED'))
    ,ORDER_DATE       DATE            NOT NULL                                -- 이 주문이 유효한 거래일
    ,TRADE_ID         BIGINT          REFERENCES T_MOCK_TRADE (ID)            -- 체결된 거래
    ,INS_DAY          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 체결 대기 주문만 빠르게 찾기 (스케줄러용)
CREATE INDEX IX_MOCK_ORDER_PENDING ON T_MOCK_ORDER (ORDER_DATE) WHERE STATUS = 'PENDING';

-- 내 주문 조회용
CREATE INDEX IX_MOCK_ORDER_USER ON T_MOCK_ORDER (USER_ID, ID DESC);

-- 2. 거래 내역에 "어느 지정가 주문으로 체결됐는지" (시장가는 NULL)
ALTER TABLE T_MOCK_TRADE ADD COLUMN ORDER_ID BIGINT;
