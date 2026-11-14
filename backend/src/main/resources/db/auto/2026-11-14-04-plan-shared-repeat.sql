-- ============================================
-- 🗓️ 내 약속: 💕 커플과 함께 · 🔁 반복
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - SHARED_YN : 'Y'면 커플인 상대의 약속 목록 · 알림에도 나옴 (지울 수 있는 사람은 만든 사람)
-- - REPEAT_ID : 반복 약속 묶음 번호 (매주 반복을 여러 줄로 미리 만들어 두고, 같은 번호로 한꺼번에 지움). 반복이 아니면 비어 있음
-- ============================================
ALTER TABLE T_PLAN ADD COLUMN IF NOT EXISTS SHARED_YN CHAR(1) NOT NULL DEFAULT 'N';

ALTER TABLE T_PLAN ADD COLUMN IF NOT EXISTS REPEAT_ID BIGINT;

CREATE INDEX IF NOT EXISTS IX_PLAN_REPEAT ON T_PLAN (REPEAT_ID);
