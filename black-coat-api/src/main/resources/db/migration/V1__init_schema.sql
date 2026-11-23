-- ============================================================================
-- Black-Coat Flyway Migration V1: Initial Schema
-- PostgreSQL with pgvector and full-text search
-- ============================================================================

-- Ensure required extensions exist
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ----------------------------------------------------------------------------
-- 1. Users Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    lang_pref VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- 2. Refresh Tokens Table (Rotation & Revocation)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_lookup ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;

-- ----------------------------------------------------------------------------
-- 3. Conversations Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conversations_user ON conversations(user_id, updated_at DESC);

-- ----------------------------------------------------------------------------
-- 4. Messages Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    lang VARCHAR(10) DEFAULT 'en',
    citations JSONB DEFAULT '[]'::jsonb,
    model VARCHAR(100),
    latency_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at ASC);

-- ----------------------------------------------------------------------------
-- 5. Daily Usage & Quotas (10/day, 100/month)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usage_daily (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day DATE NOT NULL DEFAULT CURRENT_DATE,
    count INT NOT NULL DEFAULT 0 CHECK (count >= 0),
    PRIMARY KEY (user_id, day)
);

CREATE INDEX IF NOT EXISTS idx_usage_daily_user_day ON usage_daily(user_id, day DESC);

-- ----------------------------------------------------------------------------
-- 6. Feedback Table (Thumbs up/down + Comment)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating IN (-1, 1)),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_message ON feedback(message_id);

-- ----------------------------------------------------------------------------
-- 7. Legal Documents (Acts, Codes, Amendments)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS legal_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(50) NOT NULL, -- 'ACT', 'CODE', 'CONSTITUTION', 'AMENDMENT'
    title TEXT NOT NULL,
    short_name VARCHAR(50) NOT NULL, -- 'BNS', 'BNSS', 'BSA', 'IPC', etc.
    year INT NOT NULL,
    source_url TEXT,
    license_note TEXT,
    corpus_version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_legal_docs_corpus ON legal_documents(corpus_version, short_name);

-- ----------------------------------------------------------------------------
-- 8. Legal Sections (Granular Law Corpus with HNSW Vectors & FTS)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS legal_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES legal_documents(id) ON DELETE CASCADE,
    section_no VARCHAR(50) NOT NULL,
    heading TEXT,
    text TEXT NOT NULL,
    text_tsv TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(heading, '') || ' ' || coalesce(text, ''))
    ) STORED,
    embedding vector(1024),
    corpus_version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_legal_section_version UNIQUE(document_id, section_no, corpus_version)
);

-- Full-Text Search GIN index
CREATE INDEX IF NOT EXISTS idx_legal_sections_tsv ON legal_sections USING gin(text_tsv);

-- HNSW Vector Cosine index (m = 16, ef_construction = 64)
CREATE INDEX IF NOT EXISTS idx_legal_sections_hnsw ON legal_sections 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Lookup index for citation verification
CREATE INDEX IF NOT EXISTS idx_legal_sections_lookup ON legal_sections(corpus_version, section_no);

-- ----------------------------------------------------------------------------
-- 9. Atomic Quota Management Functions
-- ----------------------------------------------------------------------------

-- Function to atomically reserve 1 query quota if daily & monthly limits permit
CREATE OR REPLACE FUNCTION reserve_user_quota(
    p_user_id UUID,
    p_daily_limit INT DEFAULT 10,
    p_monthly_limit INT DEFAULT 100
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_monthly_total INT;
    v_updated_count INT;
BEGIN
    -- Check monthly sum across current calendar month
    SELECT COALESCE(SUM(count), 0)
    INTO v_monthly_total
    FROM usage_daily
    WHERE user_id = p_user_id
      AND day >= date_trunc('month', CURRENT_DATE);

    IF v_monthly_total >= p_monthly_limit THEN
        RETURN FALSE;
    END IF;

    -- Atomic upsert guarded by daily count limit
    INSERT INTO usage_daily (user_id, day, count)
    VALUES (p_user_id, CURRENT_DATE, 1)
    ON CONFLICT (user_id, day)
    DO UPDATE SET count = usage_daily.count + 1
    WHERE usage_daily.count < p_daily_limit
    RETURNING count INTO v_updated_count;

    IF v_updated_count IS NULL THEN
        -- Daily limit already reached
        RETURN FALSE;
    END IF;

    RETURN TRUE;
END;
$$;

-- Function to refund a reserved quota on pipeline failure or "no law found"
CREATE OR REPLACE FUNCTION refund_user_quota(
    p_user_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE usage_daily
    SET count = GREATEST(count - 1, 0)
    WHERE user_id = p_user_id
      AND day = CURRENT_DATE;
END;
$$;
