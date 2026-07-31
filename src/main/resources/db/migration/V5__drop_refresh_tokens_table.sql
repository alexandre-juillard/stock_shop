-- =========================================================
-- V5 : suppression de refresh_tokens (superseded par user_sessions)
-- Le jeton de rafraichissement est desormais un UUID aleatoire
-- hache et stocke dans user_sessions (deja present depuis V1),
-- conformement a la regle metier du ticket connexion.
-- =========================================================

DROP TABLE IF EXISTS refresh_tokens;

