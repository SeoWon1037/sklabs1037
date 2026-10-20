-- ============================================
-- 과거 시세 받은 날 기록 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 최근 상장한 종목은 과거 시세가 원래 150일보다 짧아서, 서버를 켤 때마다 "이력 부족"으로 다시 받던 문제
--   → 한 번 받았으면 HISTORY_DAY에 날짜를 남기고 다시 받지 않음
-- - 지금 시세가 있는 종목은 이미 한 번 받은 것으로 표시
-- ============================================
ALTER TABLE T_STOCK ADD COLUMN HISTORY_DAY DATE;   -- 과거 시세(initial-count일치)를 받은 날

UPDATE T_STOCK S
   SET HISTORY_DAY = CURRENT_DATE
 WHERE 1=1
   AND EXISTS (SELECT 1 FROM T_STOCK_PRICE P WHERE P.STOCK_ID = S.ID);
