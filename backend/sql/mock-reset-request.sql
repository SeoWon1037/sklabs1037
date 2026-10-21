-- ============================================
-- ♻️ 모의투자 계좌 초기화 신청 (관리자가 승인해야 초기화) — pgAdmin에서 한 번만, 배포 전에
-- - 사용자: "초기화 신청" → PENDING (한 사람당 기다리는 신청 1개)
-- - 관리자: /admin/resets 에서 승인(APPROVED → 그때 초기화) / 거절(REJECTED, 한마디)
-- - 사용자가 직접 취소하면 CANCELED
-- ============================================
CREATE TABLE T_MOCK_RESET_REQUEST (
     ID             BIGSERIAL       PRIMARY KEY
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,REASON         VARCHAR(200)                                     -- 신청 이유 (선택)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'PENDING'
                    CHECK (STATUS IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
    ,ADMIN_NOTE     VARCHAR(200)                                     -- 거절 이유 등
    ,HANDLED_BY     BIGINT          REFERENCES T_USER (ID)
    ,HANDLED_DAY    TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
-- 한 사람당 기다리는 신청은 1개
CREATE UNIQUE INDEX UX_MOCK_RESET_PENDING ON T_MOCK_RESET_REQUEST (USER_ID) WHERE STATUS = 'PENDING';
CREATE INDEX IX_MOCK_RESET_LIST ON T_MOCK_RESET_REQUEST (STATUS, ID DESC);
