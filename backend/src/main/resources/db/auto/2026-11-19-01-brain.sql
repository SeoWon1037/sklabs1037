-- ============================================
-- 🧠 세컨드 브레인 (내 메모를 모아 두고, AI가 주제별로 정리하고, 뇌 모양 그래프로 보여 줌)
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - T_BRAIN_NOTE : 노트 하나
--     KIND   RAW(내가 남긴 메모 · 원본) / WIKI(AI가 메모를 모아 정리한 문서) / OUTPUT(그걸로 만든 결과물: 글 · 면접 답변 등)
--     TOPICS 주제 태그를 쉼표로 ("spring,jpa") — 그래프의 허브가 됨
-- - T_BRAIN_LINK : 노트 사이의 연결 (WIKI → 정리할 때 쓴 RAW, OUTPUT → 바탕이 된 WIKI)
-- - 본인 것만 보임 (모든 조회에 USER_ID 조건)
-- ============================================
CREATE TABLE IF NOT EXISTS T_BRAIN_NOTE (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL
    ,KIND           VARCHAR(10)     NOT NULL DEFAULT 'RAW'
    ,TITLE          VARCHAR(120)    NOT NULL
    ,BODY           TEXT            NOT NULL
    ,TOPICS         VARCHAR(200)    NOT NULL DEFAULT ''
    ,CREATED_AT     TIMESTAMP       NOT NULL DEFAULT NOW()
    ,UPDATED_AT     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_BRAIN_NOTE_USER ON T_BRAIN_NOTE (USER_ID, UPDATED_AT DESC);

CREATE TABLE IF NOT EXISTS T_BRAIN_LINK (
     FROM_ID        BIGINT          NOT NULL
    ,TO_ID          BIGINT          NOT NULL
    ,USER_ID        BIGINT          NOT NULL
    ,CREATED_AT     TIMESTAMP       NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (FROM_ID, TO_ID)
);

CREATE INDEX IF NOT EXISTS IX_BRAIN_LINK_USER ON T_BRAIN_LINK (USER_ID);

CREATE INDEX IF NOT EXISTS IX_BRAIN_LINK_TO ON T_BRAIN_LINK (TO_ID);
