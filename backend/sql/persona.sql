-- ============================================
-- 🧭 투자 성향 테스트 결과 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 12문항 → 4가지 축 점수 → 8가지 유형 (계산은 화면에서, 여기는 "나와 같은 유형이 몇 %" 통계용)
-- - 로그인 안 해도 저장 (USER_ID NULL), 같은 접속에서는 여러 번 해도 3번까지만 저장
-- ============================================
CREATE TABLE T_PERSONA_RESULT (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          REFERENCES T_USER (ID)
    ,TYPE_CODE      VARCHAR(3)      NOT NULL CHECK (TYPE_CODE IN ('SLD', 'SLN', 'SQD', 'SQN', 'RLD', 'RLN', 'RQD', 'RQN'))
    ,RISK           INT             NOT NULL     -- + 공격 / - 안정
    ,HORIZON        INT             NOT NULL     -- + 장기 / - 단기
    ,BASIS          INT             NOT NULL     -- + 데이터 / - 뉴스·이야기
    ,DISCIPLINE     INT             NOT NULL     -- + 원칙 / - 직감
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IX_PERSONA_RESULT_TYPE ON T_PERSONA_RESULT (TYPE_CODE);
