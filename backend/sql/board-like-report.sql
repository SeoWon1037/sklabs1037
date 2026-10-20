-- ============================================
-- 토론방 좋아요 · 신고 (pgAdmin에서 한 번만 실행, 배포 전에)
-- - 좋아요: 한 사람이 한 글에 한 번 (다시 누르면 취소), 내 글에는 못 누름
-- - 신고: 한 사람이 한 글에 한 번, 처리 안 된(OPEN) 신고가 3개 이상이면 글을 가림
--   관리자 화면(/admin/reports)에서 "삭제" 또는 "문제 없음"으로 처리
-- ============================================
CREATE TABLE T_STOCK_POST_LIKE (
     POST_ID        BIGINT          NOT NULL REFERENCES T_STOCK_POST (ID)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,PRIMARY KEY (POST_ID, USER_ID)
);

CREATE TABLE T_STOCK_POST_REPORT (
     ID             BIGSERIAL       PRIMARY KEY
    ,POST_ID        BIGINT          NOT NULL REFERENCES T_STOCK_POST (ID)
    ,USER_ID        BIGINT          NOT NULL REFERENCES T_USER (ID)
    ,REASON         VARCHAR(10)     NOT NULL CHECK (REASON IN ('SPAM', 'ABUSE', 'AD', 'FALSE', 'ETC'))   -- 도배 / 욕설·비방 / 광고 / 허위 정보 / 기타
    ,MEMO           VARCHAR(200)
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'OPEN' CHECK (STATUS IN ('OPEN', 'DISMISSED', 'DELETED'))
    ,HANDLED_BY     BIGINT          REFERENCES T_USER (ID)     -- 처리한 관리자
    ,HANDLED_DAY    TIMESTAMPTZ
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UNIQUE (POST_ID, USER_ID)
);

-- 처리할 신고만 빠르게 (글 목록의 가림 판단 + 관리자 화면)
CREATE INDEX IX_STOCK_POST_REPORT_OPEN ON T_STOCK_POST_REPORT (POST_ID) WHERE STATUS = 'OPEN';
