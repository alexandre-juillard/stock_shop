-- =========================================================
-- V8 : table des codes d'échange OAuth2 (redirection mobile)
-- L'API étant stateless et consommée par une app mobile native (deep link,
-- pas de lecture directe du JSON de réponse depuis une WebView/navigateur
-- système), le callback OAuth2 redirige vers l'app avec un code à usage
-- unique (haché) plutôt que de renvoyer les jetons directement dans la
-- réponse HTTP. Ce code est échangé côté app via GET /api/auth/oauth2/exchange.
-- =========================================================

CREATE TABLE oauth_exchange_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash   VARCHAR(255) NOT NULL,
    payload     TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_oauth_exchange_codes_code_hash UNIQUE (code_hash)
);
CREATE INDEX idx_oauth_exchange_codes_code_hash ON oauth_exchange_codes (code_hash);

