-- Proposition entreprise AU_CI / A_PAYER sur le bulletin (avant visa DGD)
ALTER TABLE ligne_bulletin_liquidation
    ADD COLUMN affectation_entreprise VARCHAR(10) NULL COMMENT 'AU_CI | A_PAYER — proposition entreprise';

-- Données existantes : si une affectation DGD était déjà posée, la recopier comme proposition
UPDATE ligne_bulletin_liquidation
SET affectation_entreprise = affectation
WHERE affectation IS NOT NULL
  AND (affectation_entreprise IS NULL OR affectation_entreprise = '');
