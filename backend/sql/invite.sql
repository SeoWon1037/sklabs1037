-- ============================================
-- 🤝 친구 초대 (pgAdmin에서 한 번만 실행, push 전에!)
-- - 내 초대 링크(https://sklabs1037.com/?ref=코드)로 들어와 가입하면 두 사람 모두 배지
-- - 한 사람은 한 번만 초대받을 수 있음 (INVITEE_ID가 기본키)
-- - 초대 코드는 회원 번호로 계산해서 만듦 → 따로 저장하지 않음 (InviteRules)
-- ============================================
CREATE TABLE IF NOT EXISTS T_INVITE (
     INVITEE_ID     BIGINT          PRIMARY KEY REFERENCES T_USER (ID) ON DELETE CASCADE
    ,INVITER_ID     BIGINT          NOT NULL REFERENCES T_USER (ID) ON DELETE CASCADE
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,CHECK (INVITEE_ID <> INVITER_ID)
);

CREATE INDEX IF NOT EXISTS IX_INVITE_INVITER ON T_INVITE (INVITER_ID, INS_DAY DESC);
