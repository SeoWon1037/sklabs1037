-- ============================================
-- 🛍️ 중고거래 3차: 💰 가격 제안 (market-plus.sql 다음에, pgAdmin에서 한 번만 실행, push 전에!)
-- - 사는 사람이 "이 가격에 살게요"를 보내면 판매자가 수락/거절
-- - 한 상품에 한 사람이 기다리는 제안은 하나만 (새로 보내면 앞의 것은 취소), 상품당 3번까지
-- - 수락해도 상품 가격·상태는 그대로: 두 사람 채팅방에 수락 메시지를 보내고 거래는 대화로
-- ============================================
CREATE TABLE IF NOT EXISTS T_MARKET_OFFER (
     ID             BIGSERIAL       PRIMARY KEY
    ,ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,BUYER_ID       BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,PRICE          BIGINT          NOT NULL CHECK (PRICE > 0)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'PENDING' CHECK (STATUS IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED'))
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS UX_MARKET_OFFER_PENDING ON T_MARKET_OFFER (ITEM_ID, BUYER_ID) WHERE STATUS = 'PENDING';
CREATE INDEX IF NOT EXISTS IX_MARKET_OFFER_ITEM ON T_MARKET_OFFER (ITEM_ID, STATUS);

-- 📍 지역 필터 · 📊 시세용
CREATE INDEX IF NOT EXISTS IX_MARKET_ITEM_REGION ON T_MARKET_ITEM (REGION) WHERE DEL_DAY IS NULL;
