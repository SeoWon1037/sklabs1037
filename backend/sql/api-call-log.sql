-- ============================================
-- 🔌 외부 연동 기록 (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 외부 서비스를 한 번 부를 때마다 1줄: 토스, Claude, Voyage, 뉴스 RSS, 환율, 카카오·네이버 로그인, 메일(SMTP), 푸시, GitHub
-- - 서버가 메모리에 모아 뒀다가 10초마다 한꺼번에 저장 (시세 수집 3,900번이 DB를 3,900번 두드리지 않게)
-- - 관리자 > 🔌 외부 연동 화면에서 보고, 15분 동안 실패가 많으면 관리자에게 메일 + 📲
-- - 30일 지난 기록은 매일 새벽 3시 자동 삭제
-- - 키·토큰·주소 뒤 조건(?symbols=...)·응답 내용은 저장하지 않음 (경로와 결과 숫자만)
-- ============================================
CREATE TABLE IF NOT EXISTS T_API_CALL_LOG (
     ID             BIGSERIAL       PRIMARY KEY
    ,SERVICE        VARCHAR(20)     NOT NULL                                 -- TOSS, CLAUDE, VOYAGE, NEWS_RSS, FX, KAKAO, NAVER, SMTP, PUSH, GITHUB
    ,ACTION         VARCHAR(60)     NOT NULL                                 -- prices, candles, oauth2/token, messages, send ...
    ,SUCCESS_YN     CHAR(1)         NOT NULL CHECK (SUCCESS_YN IN ('Y', 'N'))
    ,STATUS_CODE    INTEGER                                                  -- HTTP 응답 번호 (연결 자체가 실패하면 NULL)
    ,ELAPSED_MS     INTEGER         NOT NULL                                 -- 걸린 시간 (밀리초)
    ,ERROR_MSG      VARCHAR(300)                                             -- 실패 이유 (성공이면 NULL)
    ,CALLED_AT      TIMESTAMPTZ     NOT NULL                                 -- 부른 시각 (저장 시각 아님)
);

-- 최근 기록, 오래된 기록 삭제
CREATE INDEX IF NOT EXISTS IX_API_CALL_LOG_CALLED ON T_API_CALL_LOG (CALLED_AT DESC);

-- 서비스별 오늘 · 최근 15분
CREATE INDEX IF NOT EXISTS IX_API_CALL_LOG_SERVICE ON T_API_CALL_LOG (SERVICE, CALLED_AT DESC);

-- 최근 실패만 빨리 (실패는 드물어서 부분 색인이 작고 빠름)
CREATE INDEX IF NOT EXISTS IX_API_CALL_LOG_FAIL ON T_API_CALL_LOG (CALLED_AT DESC) WHERE SUCCESS_YN = 'N';
