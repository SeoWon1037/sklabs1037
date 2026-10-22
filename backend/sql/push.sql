-- ============================================
-- 📲 휴대폰·PC 푸시 알림 (pgAdmin에서 한 번만 실행, push 전에!)
-- - T_PUSH_KEY : 서버 VAPID 키 (서버가 처음 켜질 때 스스로 만들어 1줄 저장, 사람이 만질 필요 없음)
-- - T_PUSH_SUB : 알림을 켠 브라우저 (기기마다 1줄, 같은 브라우저에서 다른 계정으로 켜면 그 계정으로 옮김)
-- ============================================
CREATE TABLE IF NOT EXISTS T_PUSH_KEY (
     ID             INTEGER         PRIMARY KEY CHECK (ID = 1)
    ,PUBLIC_KEY     VARCHAR(200)    NOT NULL       -- base64url, 65바이트
    ,PRIVATE_KEY    VARCHAR(200)    NOT NULL       -- base64url, 32바이트 (화면·API로 절대 안 나감)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS T_PUSH_SUB (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,ENDPOINT       VARCHAR(1000)   NOT NULL UNIQUE
    ,P256DH         VARCHAR(200)    NOT NULL
    ,AUTH           VARCHAR(100)    NOT NULL
    ,DEVICE         VARCHAR(40)                    -- "Android · Chrome" 같은 짧은 이름
    ,FAIL_COUNT     INTEGER         NOT NULL DEFAULT 0
    ,LAST_OK_DAY    TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_PUSH_SUB_USER ON T_PUSH_SUB (USER_ID);
