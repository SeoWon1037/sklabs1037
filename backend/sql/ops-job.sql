-- ============================================
-- 작업 실행 기록 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 스케줄러가 돌 때마다 1줄: 시세 수집, 뉴스 수집·요약, 모의투자 기록 등
-- - 관리자 > 운영 상태 화면에서 보고, 실패하거나 정해진 시간에 안 돌면 관리자에게 메일
-- - 1분마다 도는 작업(지정가 체결, 가격 알림)은 실패했을 때만 기록
-- - 60일 지난 기록은 매일 새벽 자동 삭제
-- ============================================
CREATE TABLE T_JOB_RUN (
     ID             BIGSERIAL       PRIMARY KEY
    ,JOB_NAME       VARCHAR(30)     NOT NULL                                 -- STOCK_DAILY, NEWS_SUMMARY ...
    ,TRIGGER_TYPE   VARCHAR(10)     NOT NULL                                 -- SCHEDULE / STARTUP / MANUAL / WATCHDOG
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'RUNNING'
                    CHECK (STATUS IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAIL', 'ABORTED'))
    ,TOTAL_COUNT    INTEGER                                                  -- 처리한 수 (종목 수, 저장 건수 등)
    ,FAIL_COUNT     INTEGER                                                  -- 실패한 수
    ,MESSAGE        VARCHAR(1000)                                            -- 결과 요약 또는 에러 내용
    ,STARTED_AT     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,ENDED_AT       TIMESTAMPTZ
);

-- 작업별 최근 실행 (화면, 감시)
CREATE INDEX IX_JOB_RUN_NAME ON T_JOB_RUN (JOB_NAME, STARTED_AT DESC);

-- 최근 실행 목록
CREATE INDEX IX_JOB_RUN_STARTED ON T_JOB_RUN (STARTED_AT DESC);
