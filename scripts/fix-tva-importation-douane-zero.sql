-- ============================================================
-- Correction : tva_importation_douane = 0 sur des certificats OUVERT
-- Cause : l'ouverture du certificat n'initialisait pas ce champ.
-- Solution : réinitialiser à partir de tva_importation_douane_accordee
--            pour les certificats OUVERT dont le champ est 0 ou NULL
--            ET qui n'ont aucune liquidation TVA déjà effectuée.
-- ============================================================

-- Diagnostic : combien de certificats sont concernés ?
SELECT id, numero, statut,
       tva_importation_douane_accordee,
       tva_importation_douane
FROM certificat_credit
WHERE statut = 'OUVERT'
  AND (tva_importation_douane IS NULL OR tva_importation_douane = 0)
  AND tva_importation_douane_accordee IS NOT NULL
  AND tva_importation_douane_accordee > 0;

-- Correction : réinitialiser tva_importation_douane = tva_importation_douane_accordee
-- ATTENTION : à n'appliquer que si aucune liquidation réelle n'a déjà été faite sur ce certificat.
-- Si des liquidations existent, ajuster manuellement en soustrayant les montants TVA déjà débités.
UPDATE certificat_credit
SET tva_importation_douane = tva_importation_douane_accordee
WHERE statut = 'OUVERT'
  AND (tva_importation_douane IS NULL OR tva_importation_douane = 0)
  AND tva_importation_douane_accordee IS NOT NULL
  AND tva_importation_douane_accordee > 0
  AND id NOT IN (
      -- Exclure les certificats ayant déjà des liquidations TVA douane
      SELECT DISTINCT uc.certificat_credit_id
      FROM utilisation_credit uc
      WHERE uc.statut IN ('LIQUIDEE', 'CLOTUREE')
        AND uc.type_utilisation = 'DOUANIER'
  );

-- Vérification post-correction
SELECT id, numero, statut,
       tva_importation_douane_accordee,
       tva_importation_douane,
       solde_cordon,
       droits_et_taxes_douane_hors_tva
FROM certificat_credit
WHERE statut = 'OUVERT'
ORDER BY id;
