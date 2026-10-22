-- ============================================
-- 📮 문의함 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 사용자가 사이트 안에서 문의 → AI가 분류(종류·영역·심각도·요약·고칠 곳 힌트·답장 초안·자동 수정 후보)
-- - 서버 규칙이 위험한 요청(권한·비밀번호·삭제·지시 무시 등)을 먼저 걸러 RISK_FLAGS에 남기고 자동 수정 후보에서 뺌
-- - 다음 단계(자동 코드 수정)는 AUTOFIX = TRUE 인 문의만 대상
-- ============================================
CREATE TABLE IF NOT EXISTS T_INQUIRY (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,CATEGORY       VARCHAR(10)     NOT NULL CHECK (CATEGORY IN ('BUG', 'FEATURE', 'QUESTION', 'OTHER'))
    ,TITLE          VARCHAR(60)     NOT NULL
    ,BODY           VARCHAR(2000)   NOT NULL
    ,PAGE           VARCHAR(200)                          -- 어느 화면에서 썼는지
    ,USER_AGENT     VARCHAR(200)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'OPEN' CHECK (STATUS IN ('OPEN', 'ANSWERED', 'CLOSED'))
    -- AI 분류
    ,AI_TYPE        VARCHAR(10)                           -- BUG / FEATURE / QUESTION / OTHER / SPAM
    ,AI_AREA        VARCHAR(20)
    ,AI_SEVERITY    VARCHAR(10)                           -- LOW / MEDIUM / HIGH
    ,AI_SUMMARY     VARCHAR(120)
    ,AI_FIX_HINT    VARCHAR(300)
    ,AI_REPLY       VARCHAR(600)
    ,AUTOFIX        BOOLEAN         NOT NULL DEFAULT FALSE
    ,RISK_FLAGS     VARCHAR(200)
    ,TRIAGE_STATUS  VARCHAR(10)     NOT NULL DEFAULT 'WAITING' CHECK (TRIAGE_STATUS IN ('WAITING', 'DONE', 'FAILED', 'RULES'))
    ,TRIAGED_DAY    TIMESTAMPTZ
    -- 관리자 답변
    ,REPLY          VARCHAR(2000)
    ,REPLIED_BY     BIGINT          REFERENCES T_USER (ID)
    ,REPLIED_DAY    TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS IX_INQUIRY_USER ON T_INQUIRY (USER_ID, ID DESC);
CREATE INDEX IF NOT EXISTS IX_INQUIRY_STATUS ON T_INQUIRY (STATUS, ID DESC);
