-- ============================================
-- 📰 오늘의 뉴스 퀴즈 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 매일 07:40 최근 뉴스 제목으로 Claude가 5문제 출제 (문제마다 근거 기사 연결, 서버가 형식·근거 확인)
-- - 로그인하면 하루 한 번 점수 기록 → 이번 주 랭킹, 연속 참여
-- ============================================
CREATE TABLE T_NEWS_QUIZ (
     QUIZ_DATE      DATE            PRIMARY KEY
    ,QUESTIONS_JSON TEXT            NOT NULL      -- [{question, options[4], answer, explanation, sources[{title, link, press}]}]
    ,NEWS_COUNT     INT                           -- 출제에 쓴 기사 수
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE T_NEWS_QUIZ_PLAY (
     QUIZ_DATE      DATE            NOT NULL REFERENCES T_NEWS_QUIZ (QUIZ_DATE)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,SCORE          INT             NOT NULL CHECK (SCORE BETWEEN 0 AND 5)
    ,ANSWERS_JSON   TEXT            NOT NULL
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (QUIZ_DATE, USER_ID)
);
