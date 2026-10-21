-- ============================================
-- 📣 뷰티 랩 광고 문구 검수 기록 종류 추가 (pgAdmin에서 한 번만 실행, push 전에!)
-- - ADCOPY : AI로 고쳐 쓰기 (하루 20번 세기 · 최근 기록 다시 보기)
-- - 규칙 검사(AI 없음)는 기록을 남기지 않음
-- ============================================
ALTER TABLE T_BEAUTY_LOG DROP CONSTRAINT IF EXISTS T_BEAUTY_LOG_KIND_CHECK;
ALTER TABLE T_BEAUTY_LOG ADD CONSTRAINT T_BEAUTY_LOG_KIND_CHECK CHECK (KIND IN ('ANALYZE', 'BRIEF', 'COMPARE', 'ASK', 'ADCOPY'));
