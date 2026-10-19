-- ============================================
-- 모의투자 일별 자산 기록 (수익률 그래프 + 주간 랭킹용, pgAdmin에서 한 번만 실행)
-- - 평일 15:40(장 마감 후) 거래한 적 있는 계좌마다 하루 한 줄 저장
-- - 같은 날 다시 저장하면 덮어씀 (UPSERT)
-- - 계좌를 초기화하면 그 사람 기록도 지움 (그래프가 처음부터 다시 시작)
-- ============================================
CREATE TABLE T_MOCK_SNAPSHOT (
     USER_ID       BIGINT          NOT NULL REFERENCES T_MOCK_ACCOUNT (USER_ID)
    ,SNAP_DATE     DATE            NOT NULL                 -- 기록한 거래일
    ,TOTAL_ASSET   NUMERIC(15, 0)  NOT NULL                 -- 총자산 (현금 + 주문에 묶인 돈 + 주식 평가금액)
    ,RETURN_RATE   NUMERIC(9, 2)   NOT NULL                 -- 시작 자금 대비 수익률 (%)
    ,INS_DAY       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (USER_ID, SNAP_DATE)
);
