-- =========================================================
-- V6 : table des contextes de liaison OAuth2 en attente
-- Jeton a usage unique (hache), permettant de proposer une liaison de
-- compte (AUTH-007) sans dependre d'une session HTTP (API stateless).
-- =========================================================

CREATE TABLE oauth_link_contexts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash         VARCHAR(255) NOT NULL,
    target_user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider           VARCHAR(50) NOT NULL,
    provider_user_id   VARCHAR(255) NOT NULL,
    provider_email     VARCHAR(255) NOT NULL,
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    avatar_url         VARCHAR(500),
    expires_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_oauth_link_contexts_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_oauth_link_contexts_token_hash ON oauth_link_contexts (token_hash);
CREATE INDEX idx_oauth_link_contexts_target_user_id ON oauth_link_contexts (target_user_id);

