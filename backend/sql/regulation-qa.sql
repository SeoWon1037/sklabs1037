-- ============================================
-- 화장품 규제 Q&A (RAG) (pgAdmin에서 한 번만 실행, 코드 배포보다 먼저!)
-- - 관리자가 규제 문서(PDF·TXT)를 올리면 → 문단 단위로 쪼개서(청크) → Voyage AI로 벡터(1024차원)로 바꿔 저장
-- - 질문도 벡터로 바꿔 "뜻이 가까운" 문단을 찾고(pgvector, 코사인 거리) → Claude가 그 문단만 근거로 답 + 출처 표시
-- - pgvector: AWS RDS PostgreSQL에 기본으로 들어 있는 확장 (CREATE EXTENSION만 하면 됨)
-- ============================================

-- 1. pgvector 켜기
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 문서
CREATE TABLE T_RAG_DOC (
     ID             BIGSERIAL       PRIMARY KEY
    ,TITLE          VARCHAR(200)    NOT NULL                 -- 문서 이름 (예: 화장품법 시행규칙)
    ,SOURCE         VARCHAR(500)                             -- 출처 (주소 또는 설명)
    ,FILE_NAME      VARCHAR(200)                             -- 올린 파일 이름
    ,STATUS         VARCHAR(10)     NOT NULL DEFAULT 'PROCESSING'
                    CHECK (STATUS IN ('PROCESSING', 'READY', 'FAIL'))
    ,PAGE_COUNT     INTEGER                                  -- 쪽 수 (PDF)
    ,CHUNK_COUNT    INTEGER         NOT NULL DEFAULT 0       -- 쪼갠 문단 수 (처리 중이면 지금까지)
    ,MESSAGE        VARCHAR(500)                             -- 실패 이유 등
    ,USER_ID        BIGINT          REFERENCES T_USER (ID)   -- 올린 관리자
    ,INS_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
    ,UPD_DAY        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 3. 문단(청크) + 벡터
CREATE TABLE T_RAG_CHUNK (
     ID             BIGSERIAL       PRIMARY KEY
    ,DOC_ID         BIGINT          NOT NULL REFERENCES T_RAG_DOC (ID) ON DELETE CASCADE
    ,SEQ            INTEGER         NOT NULL                 -- 문서 안 순서
    ,PAGE           INTEGER                                  -- 시작 쪽 (PDF)
    ,CONTENT        TEXT            NOT NULL                 -- 문단 내용
    ,EMBEDDING      vector(1024)    NOT NULL                 -- Voyage 임베딩
);

-- 4. 가까운 문단 빠르게 찾기 (HNSW, 코사인 거리)
CREATE INDEX IX_RAG_CHUNK_EMBEDDING ON T_RAG_CHUNK USING hnsw (EMBEDDING vector_cosine_ops);
CREATE INDEX IX_RAG_CHUNK_DOC ON T_RAG_CHUNK (DOC_ID, SEQ);
