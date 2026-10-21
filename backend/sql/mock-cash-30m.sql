-- ============================================
-- 💰 모의투자 시작 자금 1,000만 → 3,000만 원 (기존 계좌에 2,000만 원 추가)
-- - 보유 종목 · 거래 기록은 그대로, 현금 +2,000만 · 시작 금액 3,000만
--   → 번 돈(원)은 그대로, 수익률은 3,000만 원 기준으로 다시 계산됨
-- - 수익률 그래프(일별 기록) · AI 매매 일지의 총자산도 +2,000만 원으로 맞춤 (그래프가 갑자기 뛰지 않게)
-- - 시작 금액이 1,000만 원인 계좌만 바꿔서, 실수로 두 번 실행해도 두 번 더해지지 않음
-- - 배포가 끝난 뒤 실행 (배포 전에 새로 생긴 1,000만 원 계좌까지 같이 바뀜)
-- ============================================
BEGIN;

UPDATE T_MOCK_SNAPSHOT S
   SET TOTAL_ASSET = S.TOTAL_ASSET + 20000000
      ,RETURN_RATE = ROUND((S.TOTAL_ASSET + 20000000 - 30000000) * 100.0 / 30000000, 2)
  FROM T_MOCK_ACCOUNT A
 WHERE A.USER_ID   = S.USER_ID
   AND A.INIT_CASH = 10000000;

UPDATE T_AI_TRADE_LOG L
   SET TOTAL_ASSET = L.TOTAL_ASSET + 20000000
  FROM T_MOCK_ACCOUNT A
 WHERE A.USER_ID   = L.USER_ID
   AND A.INIT_CASH = 10000000
   AND L.TOTAL_ASSET IS NOT NULL;

UPDATE T_MOCK_ACCOUNT
   SET CASH      = CASH + 20000000
      ,INIT_CASH = 30000000
      ,UPD_DAY   = NOW()
 WHERE INIT_CASH = 10000000;

COMMIT;

-- 확인: 모두 30000000 이어야 함
SELECT INIT_CASH, COUNT(*) FROM T_MOCK_ACCOUNT GROUP BY INIT_CASH;
