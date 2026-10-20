-- ============================================
-- 뷰티 랩 기록 종류 추가 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - COMPARE : 두 제품 성분 비교
-- - ASK     : 화장품 규제 Q&A (다음 단계에서 사용)
-- ============================================
ALTER TABLE T_BEAUTY_LOG DROP CONSTRAINT IF EXISTS T_BEAUTY_LOG_KIND_CHECK;
ALTER TABLE T_BEAUTY_LOG ADD CONSTRAINT T_BEAUTY_LOG_KIND_CHECK CHECK (KIND IN ('ANALYZE', 'BRIEF', 'COMPARE', 'ASK'));
