-- =========================================================
-- V4 : préférence de langue de l'utilisateur (i18n)
-- =========================================================

ALTER TABLE users
    ADD COLUMN preferred_locale VARCHAR(5) NOT NULL DEFAULT 'fr';

-- Contrainte de format uniquement (ex: "fr", "en", "en-US") : la liste des langues
-- effectivement supportées par l'application est gérée côté code (propriété
-- app.i18n.supported-locales), pas par une contrainte de valeurs ici. Ajouter une
-- nouvelle langue ne nécessite donc aucune nouvelle migration.
ALTER TABLE users
    ADD CONSTRAINT chk_users_preferred_locale
    CHECK (preferred_locale ~ '^[a-z]{2}(-[A-Z]{2})?$');

