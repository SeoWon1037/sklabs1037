-- ============================================
-- 🤖 AI QA 봇 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - GitHub Actions가 매일 08:30 + 배포 직후 진짜 브라우저(Chromium)로 운영 사이트 주요 화면을 휴대폰·PC 크기로 열고 눌러봄
--   → 콘솔 에러, 실패한 API, 가로 넘침, 느린 화면, 기능 흐름(검색·처방 검토) 결과 + 화면 캡처를 서버로 보냄
-- - 서버: 규칙으로 1차 판정 + Claude가 캡처를 보고 깨진 화면을 찾아 보고서 → 문제 있으면 관리자 메일
-- - 최근 30번만 보관 (캡처가 커서)
-- ============================================
CREATE TABLE T_QA_RUN (
     ID             BIGSERIAL       PRIMARY KEY
    ,SOURCE         VARCHAR(20)     NOT NULL                 -- SCHEDULE(매일) / DEPLOY(배포 직후) / MANUAL
    ,COMMIT_SHA     VARCHAR(40)                              -- 점검한 배포 커밋
    ,STATUS         VARCHAR(10)     NOT NULL CHECK (STATUS IN ('ANALYZING', 'PASS', 'WARN', 'FAIL'))
    ,SUMMARY        VARCHAR(1000)                            -- 한 줄 요약 (AI)
    ,FINDINGS_JSON  TEXT                                     -- 문제 목록 [{severity, page, viewport, title, detail, by: RULE|AI}]
    ,CHECKS_JSON    TEXT                                     -- 화면별 측정값 (로딩 시간, 콘솔 에러, 실패 요청, 가로 넘침, 흐름 결과)
    ,SHOTS_JSON     TEXT                                     -- 화면 캡처 {key: base64 jpeg}
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IX_QA_RUN_DAY ON T_QA_RUN (ID DESC);
