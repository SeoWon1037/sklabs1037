-- ============================================
-- 🗓️ 내 약속 + 🔊 음성 브리핑 설정
-- ★ 이 폴더(db/auto)의 SQL은 서버가 켜질 때 스스로 한 번만 실행해요 — pgAdmin에서 실행하지 않아도 돼요
-- - T_PLAN        : 약속 (AI 챗봇에게 "토요일 7시 약속 잡아줘" 또는 내 약속 화면에서 추가)
--                   PLAN_AT은 한국 시간. 시간이 00:00이면 "시간 미정"
--                   알림(하루 전 · 당일 오전 9시)의 중복 방지는 알림 쪽 키(PLAN:날짜:D1/D0)로
-- - T_VOICE_BRIEF : 음성 브리핑 설정 (아침 7시 날씨 · 약속 / 장 마감 관심 종목 / 날씨 지역)
-- ============================================
CREATE TABLE IF NOT EXISTS T_PLAN (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL
    ,TITLE          VARCHAR(100)    NOT NULL
    ,PLACE          VARCHAR(100)
    ,PLAN_AT        TIMESTAMP       NOT NULL
    ,CREATED_AT     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_PLAN_USER_AT ON T_PLAN (USER_ID, PLAN_AT);

CREATE INDEX IF NOT EXISTS IX_PLAN_AT ON T_PLAN (PLAN_AT);

CREATE TABLE IF NOT EXISTS T_VOICE_BRIEF (
     USER_ID        BIGINT          PRIMARY KEY
    ,MORNING_YN     CHAR(1)         NOT NULL DEFAULT 'N'
    ,CLOSE_YN       CHAR(1)         NOT NULL DEFAULT 'N'
    ,REGION         VARCHAR(20)     NOT NULL DEFAULT 'SEOUL'
    ,UPD_DAY        TIMESTAMP       NOT NULL DEFAULT NOW()
);
