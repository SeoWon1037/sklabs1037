-- ============================================
-- 🔁 스스로 고치는 AI 비서 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 👎 받은 답변을 AI가 분석 → 시스템 프롬프트 개선안 → 예전 질문으로 새/옛 프롬프트를 둘 다 돌려 AI 심판이 채점
-- - 더 나으면 관리자가 버튼 하나로 적용 (언제든 이전 버전으로 되돌리기)
-- 1. T_AI_PROMPT      : 프롬프트 버전 (ACTIVE는 최대 1개, 없으면 코드에 있는 기본 프롬프트 = 버전 0)
-- 2. T_AI_CHAT_LOG    : 어떤 프롬프트 버전이 답했는지 (버전별 👎 비율 비교용)
-- ============================================

CREATE TABLE T_AI_PROMPT (
     ID             BIGSERIAL       PRIMARY KEY
    ,VERSION        INT             NOT NULL UNIQUE
    ,BASE_VERSION   INT             NOT NULL DEFAULT 0       -- 어느 버전을 고친 것인지
    ,CONTENT        TEXT            NOT NULL                 -- 시스템 프롬프트 전문
    ,STATUS         VARCHAR(10)     NOT NULL CHECK (STATUS IN ('CANDIDATE', 'ACTIVE', 'RETIRED', 'REJECTED'))
    ,ANALYSIS_JSON  TEXT                                     -- AI가 찾은 실패 패턴 [{problem, evidence, fix}]
    ,CHANGES_JSON   TEXT                                     -- 바꾼 점 ["...", ...]
    ,EVAL_JSON      TEXT                                     -- 질문별 채점 [{question, oldScore, newScore, winner, reason, ...}]
    ,SCORE_OLD      NUMERIC(4, 2)                            -- 옛 프롬프트 평균 점수 (10점 만점)
    ,SCORE_NEW      NUMERIC(4, 2)                            -- 새 프롬프트 평균 점수
    ,RECOMMEND_YN   CHAR(1)         NOT NULL DEFAULT 'N' CHECK (RECOMMEND_YN IN ('Y', 'N'))
    ,DOWN_COUNT     INT                                      -- 분석한 👎 수
    ,DECIDED_BY     BIGINT          REFERENCES T_USER (ID)   -- 적용·거절한 관리자
    ,DECIDED_DAY    TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 적용 중인 프롬프트는 하나만
CREATE UNIQUE INDEX UX_AI_PROMPT_ACTIVE ON T_AI_PROMPT (STATUS) WHERE STATUS = 'ACTIVE';

ALTER TABLE T_AI_CHAT_LOG ADD COLUMN PROMPT_VERSION INT NOT NULL DEFAULT 0;
