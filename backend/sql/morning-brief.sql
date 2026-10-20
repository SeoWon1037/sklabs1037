-- ============================================
-- 아침 브리핑 메일 신청 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 마이페이지에서 켠 회원에게 평일 08:00 메일 1통
--   (관심 종목 어제 종가·등락 + AI 뉴스 요약 + 시장 요약 + 모의투자 계좌)
-- - LAST_SENT_DAY: 같은 날 두 번 보내지 않게 (보내기 전에 먼저 오늘로 바꾼 경우에만 발송)
-- ============================================
CREATE TABLE T_MORNING_BRIEF (
     USER_ID        BIGINT          PRIMARY KEY REFERENCES T_USER (ID)
    ,ENABLED_YN     CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (ENABLED_YN IN ('Y', 'N'))
    ,LAST_SENT_DAY  DATE                                                     -- 마지막으로 보낸 날
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
