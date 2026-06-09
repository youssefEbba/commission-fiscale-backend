-- Colonne d'origine du stock TVA déductible (liquidation douane vs transfert).
-- À exécuter si Hibernate ddl-auto ne met pas à jour la base ou pour backfiller les lignes existantes.

ALTER TABLE tva_deductible_stock
    ADD COLUMN source VARCHAR(32) NULL COMMENT 'UTILISATION_DOUANE | TRANSFERT_CREDIT';

UPDATE tva_deductible_stock
SET source = 'UTILISATION_DOUANE'
WHERE utilisation_douane_id IS NOT NULL
  AND (source IS NULL OR source = '');

UPDATE tva_deductible_stock
SET source = 'TRANSFERT_CREDIT'
WHERE utilisation_douane_id IS NULL
  AND (source IS NULL OR source = '');

-- Optionnel : contrainte NOT NULL après vérification qu'il ne reste plus de NULL
-- ALTER TABLE tva_deductible_stock MODIFY COLUMN source VARCHAR(32) NOT NULL;
