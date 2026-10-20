-- ============================================
-- 💕 커플 맺기 (pgAdmin에서 한 번만 실행, 배포 전에)
-- - 신청한 사람이 상대 닉네임 + 시작일을 정해서 신청 → 상대가 수락하면 커플
-- - 한 사람은 커플 1개만 (수락할 때 서버가 확인)
-- - 상태: PENDING(신청 중) / ACTIVE(커플) / DECLINED(거절) / CANCELED(신청 취소) / ENDED(끊음)
-- ============================================
CREATE TABLE T_COUPLE (
     ID             BIGSERIAL       PRIMARY KEY
    ,REQUESTER_ID   BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,PARTNER_ID     BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,START_DATE     DATE            NOT NULL                         -- 신청한 사람이 정한 시작일 (이 날 = 1일)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'PENDING'
                    CHECK (STATUS IN ('PENDING', 'ACTIVE', 'DECLINED', 'CANCELED', 'ENDED'))
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,ACCEPTED_DAY   TIMESTAMPTZ
    ,ENDED_DAY      TIMESTAMPTZ
    ,CHECK (REQUESTER_ID <> PARTNER_ID)
);
CREATE INDEX IX_COUPLE_REQUESTER ON T_COUPLE (REQUESTER_ID, STATUS);
CREATE INDEX IX_COUPLE_PARTNER ON T_COUPLE (PARTNER_ID, STATUS);
