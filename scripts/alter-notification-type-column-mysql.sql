-- Corrige « Data truncated for column 'type' » (DEMANDE_EXPLICATION, PASSWORD_RESET_*, etc.)
-- Colonne type trop courte ou ENUM sans les nouvelles valeurs.
-- À exécuter une fois sur MySQL (prod / local) si la migration auto au démarrage n'a pas suffi.
ALTER TABLE notification MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE notification MODIFY COLUMN entity_type VARCHAR(64) NOT NULL;
