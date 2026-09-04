CREATE TABLE user_context_embeddings (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    embedding VECTOR(384),
    data_type VARCHAR(40) NOT NULL DEFAULT 'onboarding',
    related_category VARCHAR(40) NOT NULL DEFAULT 'cashflow',
    emotion_tag VARCHAR(40),
    urgency_level VARCHAR(20) NOT NULL DEFAULT 'normal',
    linked_schedule_id VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX user_context_embeddings_user_category_idx
    ON user_context_embeddings (user_id, related_category, created_at DESC);

CREATE INDEX user_context_embeddings_vector_idx
    ON user_context_embeddings USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
