-- ============================================
-- 전 종목 확장 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 코스피·코스닥 주식(보통주·우선주) + ETF 약 3,000개를 매일 자동 등록 (T_STOCK)
-- - 지금 있는 12종목은 "주요 종목"(FEATURED_YN = 'Y')
--   → 뉴스 수집·AI 뉴스 요약은 주요 종목만 (구글 검색·Claude 비용이 종목 수만큼 늘지 않게)
-- - 종목마다 최신 종가·전일 종가를 T_STOCK에 같이 저장
--   → 목록·시가총액·검색이 수십만 줄짜리 시세 테이블을 매번 뒤지지 않게
-- ============================================

-- 1. 새 종목을 넣을 때 ID가 자동으로 붙게 (이미 자동 번호면 아무것도 안 함)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT  1
          FROM INFORMATION_SCHEMA.COLUMNS
         WHERE 1=1
           AND TABLE_NAME  = 't_stock'
           AND COLUMN_NAME = 'id'
           AND (IS_IDENTITY = 'YES' OR COLUMN_DEFAULT IS NOT NULL)
    ) THEN
        CREATE SEQUENCE IF NOT EXISTS T_STOCK_ID_SEQ OWNED BY T_STOCK.ID;
        PERFORM SETVAL('T_STOCK_ID_SEQ', COALESCE((SELECT MAX(ID) FROM T_STOCK), 0) + 1, FALSE);
        ALTER TABLE T_STOCK ALTER COLUMN ID SET DEFAULT NEXTVAL('T_STOCK_ID_SEQ');
    END IF;
END $$;

-- 2. 새 칸
ALTER TABLE T_STOCK ADD COLUMN FEATURED_YN      CHAR(1)        NOT NULL DEFAULT 'N';   -- 주요 종목 (뉴스·AI 요약 대상)
ALTER TABLE T_STOCK ADD COLUMN SECURITY_TYPE    VARCHAR(30);                           -- STOCK / ETF
ALTER TABLE T_STOCK ADD COLUMN LAST_TRADE_DATE  DATE;                                  -- 최신 거래일
ALTER TABLE T_STOCK ADD COLUMN LAST_CLOSE       NUMERIC(15, 2);                        -- 최신 종가
ALTER TABLE T_STOCK ADD COLUMN PREV_CLOSE       NUMERIC(15, 2);                        -- 전일 종가
ALTER TABLE T_STOCK ADD COLUMN LAST_VOLUME      BIGINT;                                -- 최신 거래량

-- 3. 지금 있는 종목 = 주요 종목, 주식
UPDATE T_STOCK
   SET FEATURED_YN   = 'Y'
      ,SECURITY_TYPE = 'STOCK';

-- 4. 지금 있는 종목의 최신 종가 · 전일 종가 채우기
UPDATE T_STOCK S
   SET LAST_TRADE_DATE = L.TRADE_DATE
      ,LAST_CLOSE      = L.CLOSE_PRICE
      ,PREV_CLOSE      = L.PREV_CLOSE
      ,LAST_VOLUME     = L.VOLUME
  FROM (
        SELECT  X.STOCK_ID
               ,MAX(CASE WHEN X.RN = 1 THEN X.TRADE_DATE  END) AS TRADE_DATE
               ,MAX(CASE WHEN X.RN = 1 THEN X.CLOSE_PRICE END) AS CLOSE_PRICE
               ,MAX(CASE WHEN X.RN = 2 THEN X.CLOSE_PRICE END) AS PREV_CLOSE
               ,MAX(CASE WHEN X.RN = 1 THEN X.VOLUME      END) AS VOLUME
          FROM (
                SELECT  P.STOCK_ID
                       ,P.TRADE_DATE
                       ,P.CLOSE_PRICE
                       ,P.VOLUME
                       ,ROW_NUMBER() OVER (PARTITION BY P.STOCK_ID ORDER BY P.TRADE_DATE DESC) AS RN
                  FROM T_STOCK_PRICE P
          ) X
         WHERE 1=1
           AND X.RN <= 2
         GROUP BY X.STOCK_ID
  ) L
 WHERE 1=1
   AND S.ID = L.STOCK_ID;
