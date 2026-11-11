-- ============================================
-- 🛍️ 중고거래: 상품별 하루 조회 수 (판매자에게 "최근 7일 조회" 막대로 보여줌)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
--   (실행 기록: T_SCHEMA_AUTO / 결과 확인: 관리자 > 자가 점검 > 자동 SQL)
-- - 판매자 본인이 본 것은 세지 않음, 60일 지난 줄은 매일 지움
-- ============================================
CREATE TABLE IF NOT EXISTS T_MARKET_VIEW_DAY (
     ITEM_ID        BIGINT          NOT NULL
    ,DAY            DATE            NOT NULL
    ,VIEWS          INT             NOT NULL DEFAULT 0
    ,PRIMARY KEY (ITEM_ID, DAY)
);

CREATE INDEX IF NOT EXISTS IX_MARKET_VIEW_DAY_DAY ON T_MARKET_VIEW_DAY (DAY);
