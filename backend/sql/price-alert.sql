-- ============================================
-- 목표가 알림 (pgAdmin에서 한 번만 실행)
-- - "삼성전자 220,000원 이상이 되면 메일" 같은 알림
-- - 정규장(평일 09:00~15:30) 1분마다 확인 → 조건을 넘으면 메일 1번 보내고 SENT로 바꿈
-- - SENT 알림은 마이페이지에서 "다시 켜기"로 ACTIVE로 되돌릴 수 있음
-- ============================================
CREATE TABLE T_PRICE_ALERT (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,STOCK_ID       BIGINT          NOT NULL REFERENCES T_STOCK (ID)
    ,CONDITION      VARCHAR(5)      NOT NULL CHECK (CONDITION IN ('ABOVE', 'BELOW'))   -- ABOVE: 이상 / BELOW: 이하
    ,TARGET_PRICE   NUMERIC(15, 0)  NOT NULL CHECK (TARGET_PRICE > 0)
    ,STATUS         VARCHAR(6)      NOT NULL DEFAULT 'ACTIVE' CHECK (STATUS IN ('ACTIVE', 'SENT'))
    ,SENT_PRICE     NUMERIC(15, 0)                                                      -- 알림 보낼 때 가격
    ,SENT_DAY       TIMESTAMPTZ                                                         -- 알림 보낸 시각
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 확인할 알림만 빠르게 (스케줄러)
CREATE INDEX IX_PRICE_ALERT_ACTIVE ON T_PRICE_ALERT (STOCK_ID) WHERE STATUS = 'ACTIVE';

-- 내 알림 조회용
CREATE INDEX IX_PRICE_ALERT_USER ON T_PRICE_ALERT (USER_ID, ID DESC);
