-- ─────────────────────────────────────────────────────────────────────────────
-- Correction colonne statut — table transfert_credit
--
-- Symptôme : "Data truncated for column 'statut'"
--            lors d'un VALIDER (statut = TRANSFERE) ou ANNULER (statut = ANNULEE).
--
-- Cause : la colonne a été créée en VARCHAR trop court ou en ENUM MySQL ne
--         contenant pas les nouvelles valeurs (INCOMPLETE, A_RECONTROLER,
--         TRANSFERE, ANNULEE).
--         Hibernate ddl-auto=update ne modifie PAS les colonnes ENUM MySQL.
--
-- Solution : convertir en VARCHAR(32) — à exécuter une seule fois sur la base.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE transfert_credit
    MODIFY COLUMN statut VARCHAR(32) NOT NULL;

-- Vérification (optionnel) :
-- SHOW COLUMNS FROM transfert_credit LIKE 'statut';
-- Résultat attendu : varchar(32)
