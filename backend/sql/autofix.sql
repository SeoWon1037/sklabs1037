-- ============================================
-- 🤖 AI 자동 수정 (pgAdmin에서 한 번만 실행, push 전에!)
-- 흐름: 관리자가 문의함에서 [AI로 고치기] → 서버가 GitHub에 ai-fix 이슈 → Actions의 Claude Code가 수정·테스트 → PR
--       → 서버로 결과 콜백 → 관리자 📲 알림 → [승인] → 서버가 PR을 main에 합침 → 자동 배포
-- STATUS: REQUESTED(이슈 만듦) → WORKING(Actions 시작) → READY(테스트 통과, PR 있음) / NO_CHANGE(고칠 게 없다고 판단) / FAILED
--         READY → MERGED(승인·배포) / REJECTED(거절, PR 닫음)
-- ============================================
CREATE TABLE IF NOT EXISTS T_AUTOFIX (
     ID             BIGSERIAL       PRIMARY KEY
    ,INQUIRY_ID     BIGINT          REFERENCES T_INQUIRY (ID)
    ,ISSUE_NO       INTEGER
    ,PR_NO          INTEGER
    ,BRANCH         VARCHAR(100)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'REQUESTED'
                    CHECK (STATUS IN ('REQUESTED', 'WORKING', 'READY', 'NO_CHANGE', 'FAILED', 'MERGED', 'REJECTED'))
    ,SUMMARY        VARCHAR(2000)                         -- AI가 쓴 수정 요약
    ,FILES          VARCHAR(2000)                         -- 바뀐 파일 (줄바꿈으로)
    ,ERROR          VARCHAR(1000)
    ,RUN_URL        VARCHAR(300)                          -- Actions 실행 화면
    ,REQUESTED_BY   BIGINT          REFERENCES T_USER (ID)
    ,HANDLED_BY     BIGINT          REFERENCES T_USER (ID)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS UX_AUTOFIX_ISSUE ON T_AUTOFIX (ISSUE_NO);
CREATE INDEX IF NOT EXISTS IX_AUTOFIX_INQUIRY ON T_AUTOFIX (INQUIRY_ID, ID DESC);
