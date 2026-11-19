-- ============================================
-- 🧠 세컨드 브레인: SKLabs 스프링 코드 기록 받기
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - SOURCE_KEY  : 사이트가 넣어 준 노트의 이름표 (code-ai, update-112 …). 내가 쓴 노트는 비어 있음
-- - SOURCE_HASH : 마지막으로 넣어 준 본문의 MD5 → 지금 본문과 같으면(내가 안 고쳤으면) 새 버전으로 바꿔 줌
-- - T_BRAIN_SEEN: 한 번 받은 이름표 (내가 지운 노트를 다음 업데이트 때 다시 넣지 않으려고)
-- ============================================
ALTER TABLE T_BRAIN_NOTE ADD COLUMN IF NOT EXISTS SOURCE_KEY VARCHAR(80);

ALTER TABLE T_BRAIN_NOTE ADD COLUMN IF NOT EXISTS SOURCE_HASH VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS UX_BRAIN_NOTE_SOURCE ON T_BRAIN_NOTE (USER_ID, SOURCE_KEY) WHERE SOURCE_KEY IS NOT NULL;

CREATE TABLE IF NOT EXISTS T_BRAIN_SEEN (
    USER_ID     BIGINT       NOT NULL,
    SOURCE_KEY  VARCHAR(80)  NOT NULL,
    CREATED_AT  TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (USER_ID, SOURCE_KEY)
);
