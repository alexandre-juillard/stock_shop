-- =========================================================
-- V7 : remplace le type ENUM natif "push_platform" par un VARCHAR + CHECK,
-- conformément à la convention déjà utilisée pour users.role et users.theme.
-- Évite les complexités de mapping JPA/JDBC des enums Postgres natifs
-- (STK-008 : notification push d'expiration).
-- =========================================================

ALTER TABLE push_tokens
    ALTER COLUMN platform TYPE VARCHAR(10) USING UPPER(platform::text);

DROP TYPE push_platform;

ALTER TABLE push_tokens
    ADD CONSTRAINT chk_push_tokens_platform CHECK (platform IN ('ANDROID', 'IOS'));

