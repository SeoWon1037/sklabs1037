-- ============================================
-- 🛍️ 중고거래 2차: 거래 후기 · 매너 온도 · 키워드 알림 · 가격 내림 (market.sql 다음에, pgAdmin에서 한 번만 실행, push 전에!)
-- - 판매완료 때 "누구와 거래했는지"(BUYER_ID)를 고르면 두 사람이 서로 후기를 남길 수 있음
-- - 매너 온도 = 36.5 + 좋아요 0.5 − 별로예요 1.0 (받은 후기로 계산, 따로 저장하지 않음)
-- - 키워드 알림: 등록한 낱말이 제목에 들어간 새 상품이 올라오면 알림 + 📲 (한 사람 10개)
-- - 가격을 내리면 PREV_PRICE에 전 가격을 남기고, 찜한 사람들에게 알림
-- ============================================
ALTER TABLE T_MARKET_ITEM ADD COLUMN IF NOT EXISTS BUYER_ID   BIGINT REFERENCES T_USER (ID);
ALTER TABLE T_MARKET_ITEM ADD COLUMN IF NOT EXISTS PREV_PRICE BIGINT;

CREATE TABLE IF NOT EXISTS T_MARKET_REVIEW (
     ID             BIGSERIAL       PRIMARY KEY
    ,ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,WRITER_ID      BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,TARGET_ID      BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,WRITER_ROLE    VARCHAR(6)      NOT NULL CHECK (WRITER_ROLE IN ('SELLER', 'BUYER'))
    ,RATING         SMALLINT        NOT NULL CHECK (RATING BETWEEN 1 AND 3)    -- 1 별로예요 / 2 보통이에요 / 3 좋아요
    ,TAGS           VARCHAR(200)                                               -- 고른 칭찬(쉼표로): 시간 약속을 잘 지켜요 …
    ,COMMENT        VARCHAR(300)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UNIQUE (ITEM_ID, WRITER_ID)
);

CREATE INDEX IF NOT EXISTS IX_MARKET_REVIEW_TARGET ON T_MARKET_REVIEW (TARGET_ID, ID DESC);

CREATE TABLE IF NOT EXISTS T_MARKET_KEYWORD (
     USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,KEYWORD        VARCHAR(20)     NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, KEYWORD)
);
