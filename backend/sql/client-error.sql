-- ============================================
-- 🐞 화면 오류 모으기 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 사용자 브라우저에서 난 자바스크립트 오류 · 화면이 깨진 일을 서버가 모음 (같은 오류는 한 줄에 횟수만 올림)
-- - SIG: 오류 종류 + 다듬은 메시지 + 스택 첫 줄의 지문 → 같은 오류 묶기
-- - STATUS: OPEN(새로 생김 · 다시 생김) / DONE(고침) / IGNORED(무시)
--   DONE으로 해둔 오류가 또 생기면 OPEN으로 되돌아옴
-- - 30일 동안 다시 안 생긴 줄은 자동으로 지움
-- - 주소는 경로만 저장 (검색어 같은 ?뒤 내용은 저장하지 않음)
-- ============================================
CREATE TABLE IF NOT EXISTS T_CLIENT_ERROR (
     ID             BIGSERIAL       PRIMARY KEY
    ,SIG            VARCHAR(40)     NOT NULL UNIQUE
    ,KIND           VARCHAR(10)     NOT NULL                -- ERROR(자바스크립트 오류) / PROMISE(처리 안 된 실패) / RENDER(화면 그리기 실패) / CHUNK(화면 코드 못 받음)
    ,MESSAGE        VARCHAR(300)    NOT NULL
    ,STACK          VARCHAR(1500)
    ,PATH           VARCHAR(120)
    ,UA             VARCHAR(200)
    ,CNT            INT             NOT NULL DEFAULT 1
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'OPEN' CHECK (STATUS IN ('OPEN', 'DONE', 'IGNORED'))
    ,FIRST_DAY      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,LAST_DAY       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_CLIENT_ERROR_LAST ON T_CLIENT_ERROR (STATUS, LAST_DAY DESC);
