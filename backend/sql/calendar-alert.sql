-- ============================================
-- 🔔 증시 일정 알림 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 📅 증시 일정 화면에서 켠 회원에게, 일정 전날 저녁 7시에 사이트 알림 + 📲 푸시
-- - KINDS: 받을 종류 (쉼표로) FOMC, BOK(한은 금통위), EXPIRY(옵션 만기), HOLIDAY(휴장)
-- - 끄면 줄을 지움
-- ============================================
CREATE TABLE IF NOT EXISTS T_CALENDAR_ALERT (
     USER_ID        BIGINT          PRIMARY KEY REFERENCES T_USER (ID) ON DELETE CASCADE
    ,KINDS          VARCHAR(60)     NOT NULL DEFAULT 'FOMC,BOK,EXPIRY,HOLIDAY'
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
