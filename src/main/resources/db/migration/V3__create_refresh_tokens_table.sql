-- =========================================================
-- V3 : table refresh_tokens pour la gestion des jetons JWT
-- (mécanisme distinct de user_sessions, qui gère le
-- "Se souvenir de moi" côté application mobile/web).
-- =========================================================
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token        VARCHAR(512) NOT NULL,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expiry_date  TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token)
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

