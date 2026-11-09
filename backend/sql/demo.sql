-- ============================================
-- 🧪 체험 계정 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 로그인 화면 [로그인 없이 체험하기] → 모두가 이 계정 하나로 들어옴 (면접관·처음 온 사람용)
-- - 비밀번호 없음 + PROVIDER='DEMO' + 받을 수 없는 메일 주소(.invalid) → 일반 로그인·비밀번호 찾기 불가
-- - 할 수 있는 것: 모의투자 · AI 비서 · 뷰티 랩 · 예측 게임 · 퀴즈 · 투자 성향 · 관심 종목 (서버 DemoGuard가 나머지 쓰기를 막음)
-- - 매일 05:10 모의투자 계좌 · 알림 · 가격 알림을 처음 상태로 (작업 DEMO_RESET)
-- - 수익률 랭킹 · 메시지 상대 검색에는 안 나옴
-- ============================================
INSERT INTO T_USER (EMAIL, NICKNAME, ROLE, PROVIDER, PROVIDER_ID, INS_DAY)
SELECT 'demo@sklabs1037.invalid', '체험회원', 'USER', 'DEMO', 'demo', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM T_USER WHERE PROVIDER = 'DEMO');
