-- ============================================
-- 🛍️ 중고거래 장터 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 회원이 상품(사진 최대 5장 · 카테고리 · 가격)을 올리고, 사는 사람은 [채팅하기]로 판매자와 1:1 메시지(💬 기존 메시지 기능)
-- - 결제는 없음 (당근·중고나라처럼 대화로 직접 거래)
-- - 사진은 화면에서 줄여서(긴 변 1280px + 목록용 작은 그림 360px) DB에 저장 → 누구나 볼 수 있음
-- - 상태: SALE 판매중 / RESERVED 예약중 / SOLD 판매완료 / HIDDEN 신고 3번으로 숨김
-- ============================================
CREATE TABLE IF NOT EXISTS T_MARKET_ITEM (
     ID             BIGSERIAL       PRIMARY KEY
    ,SELLER_ID      BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,CATEGORY       VARCHAR(20)     NOT NULL                     -- DIGITAL, FASHION ... (MarketRules.CATEGORIES)
    ,TITLE          VARCHAR(60)     NOT NULL
    ,PRICE          BIGINT          NOT NULL CHECK (PRICE >= 0)  -- 0 = 무료 나눔
    ,DESCRIPTION    VARCHAR(2000)   NOT NULL
    ,ITEM_COND      VARCHAR(10)     NOT NULL DEFAULT 'USED'      -- NEW 새 상품 / LIKE_NEW 거의 새것 / USED 사용감 있음
    ,TRADE_TYPE     VARCHAR(10)     NOT NULL DEFAULT 'BOTH'      -- DIRECT 직거래 / DELIVERY 택배 / BOTH 둘 다
    ,REGION         VARCHAR(30)                                  -- 거래 희망 지역 (직접 입력, 예: 서울 강남구)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'SALE' CHECK (STATUS IN ('SALE', 'RESERVED', 'SOLD', 'HIDDEN'))
    ,VIEW_CNT       INTEGER         NOT NULL DEFAULT 0
    ,LIKE_CNT       INTEGER         NOT NULL DEFAULT 0
    ,CHAT_CNT       INTEGER         NOT NULL DEFAULT 0
    ,REPORT_CNT     INTEGER         NOT NULL DEFAULT 0
    ,BUMP_DAY       TIMESTAMPTZ     NOT NULL DEFAULT NOW()       -- 목록 정렬 기준 (끌어올리면 지금으로)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,DEL_DAY        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS IX_MARKET_ITEM_LIST   ON T_MARKET_ITEM (BUMP_DAY DESC) WHERE DEL_DAY IS NULL;
CREATE INDEX IF NOT EXISTS IX_MARKET_ITEM_SELLER ON T_MARKET_ITEM (SELLER_ID, ID DESC);

-- 사진 (DATA = 상세 화면용, THUMB = 목록용 작은 그림)
CREATE TABLE IF NOT EXISTS T_MARKET_PHOTO (
     ID             BIGSERIAL       PRIMARY KEY
    ,ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,ORD            INTEGER         NOT NULL DEFAULT 0
    ,CONTENT_TYPE   VARCHAR(20)     NOT NULL
    ,BYTES          INTEGER         NOT NULL
    ,DATA           BYTEA           NOT NULL
    ,THUMB          BYTEA
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_MARKET_PHOTO_ITEM ON T_MARKET_PHOTO (ITEM_ID, ORD, ID);

-- 찜
CREATE TABLE IF NOT EXISTS T_MARKET_LIKE (
     USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, ITEM_ID)
);

-- 채팅 시작 기록 (상품마다 사는 사람 1줄 → 채팅 수 · 첫 안내 메시지는 한 번만)
CREATE TABLE IF NOT EXISTS T_MARKET_CHAT (
     ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,BUYER_ID       BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,ROOM_ID        BIGINT          NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (ITEM_ID, BUYER_ID)
);

-- 신고 (한 사람이 한 상품에 한 번, 3번 모이면 자동 숨김)
CREATE TABLE IF NOT EXISTS T_MARKET_REPORT (
     ITEM_ID        BIGINT          NOT NULL REFERENCES T_MARKET_ITEM (ID) ON DELETE CASCADE
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,REASON         VARCHAR(200)    NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (ITEM_ID, USER_ID)
);
