CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE job_jd_embeddings (
    id            BIGSERIAL PRIMARY KEY,
    posting_id    VARCHAR(100) NOT NULL,
    company_id    VARCHAR(200) NOT NULL,
    job_family    VARCHAR(100) NOT NULL,
    posted_date   DATE NOT NULL,
    chunk_text    TEXT NOT NULL,
    embedding     VECTOR(384) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_jd_company_family ON job_jd_embeddings (company_id, job_family);
CREATE INDEX idx_job_jd_embedding ON job_jd_embeddings USING hnsw (embedding vector_cosine_ops);

-- 가계부AI의 user_context_embeddings 테이블은 다음 마이그레이션(V4)에서 추가 예정.
-- CREATE EXTENSION은 여기서 이미 했으니 V4에서 다시 안 해도 됨(IF NOT EXISTS라 안전).
