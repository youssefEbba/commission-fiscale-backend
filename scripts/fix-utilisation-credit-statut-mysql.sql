-- ============================================================
-- Correction : colonne 'statut' trop courte dans utilisation_credit
-- Erreur : Data truncated for column 'statut'
-- Cause : les nouveaux statuts (ex. QUITTANCES_ENREGISTREES = 23 chars)
--         dépassent la taille VARCHAR par défaut (souvent 20-25).
-- Solution : agrandir la colonne à VARCHAR(32)
-- ============================================================

-- Vérifier la taille actuelle avant modification (optionnel)
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'utilisation_credit'
  AND COLUMN_NAME  = 'statut';

-- Appliquer la correction
ALTER TABLE utilisation_credit
    MODIFY COLUMN statut VARCHAR(32) NOT NULL;

-- Vérification post-modification
SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'utilisation_credit'
  AND COLUMN_NAME  = 'statut';
