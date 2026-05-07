-- =======================================================================
--  MIGRATION : solde_cordon = droits (b) seulement, séparé de TVA (d)
-- =======================================================================
--
--  POURQUOI
--  --------
--  Avant ce correctif, solde_cordon était initialisé à b+d (ex. 20 MRU)
--  mais seule la partie hors-TVA (droits) y était débitée.
--  Résultat : les droits consommés pouvaient dépasser b sans être bloqués
--  car la TVA (d=8) fournissait un espace fictif dans le budget.
--
--  NOUVEAU MODÈLE
--  --------------
--  solde_cordon  = droits restants        (démarre à b, diminue à chaque liquidation)
--  tva_importation_douane = TVA restante  (démarre à d, diminue à chaque liquidation)
--
--  Validation dans liquiderDouane (DGTCP) :
--    ✓  montant_droits_AU_CI ≤ solde_cordon
--    ✓  tva_AU_CI            ≤ tva_importation_douane
--
-- =======================================================================

-- -----------------------------------------------------------------------
--  ÉTAPE 1 : recalculer solde_cordon sur chaque certificat
--            = droits_et_taxes_douane_hors_tva - Σ(montant_droits liquidés)
-- -----------------------------------------------------------------------
UPDATE certificat_credit cc
LEFT JOIN (
    SELECT
        uc.certificat_credit_id,
        COALESCE(SUM(uc.montant_droits), 0) AS total_droits_consommes
    FROM utilisation_credit uc
    WHERE uc.type_utilisation = 'DOUANIER'
      AND uc.statut            = 'LIQUIDEE'
    GROUP BY uc.certificat_credit_id
) consommes ON consommes.certificat_credit_id = cc.id
SET cc.solde_cordon = GREATEST(
    0,
    COALESCE(cc.droits_et_taxes_douane_hors_tva, 0)
    - COALESCE(consommes.total_droits_consommes, 0)
)
WHERE cc.droits_et_taxes_douane_hors_tva IS NOT NULL;

-- -----------------------------------------------------------------------
--  ÉTAPE 2 : pour les certificats sans recap (droits_et_taxes_douane_hors_tva NULL),
--            garder l'ancienne logique (solde_cordon = montant_cordon initial).
--            Ces certificats ont été créés avant l'ajout du récapitulatif fiscal.
-- -----------------------------------------------------------------------
-- (rien à faire : ils restent avec leur valeur actuelle)

-- -----------------------------------------------------------------------
--  VÉRIFICATION post-migration (à exécuter manuellement)
-- -----------------------------------------------------------------------
SELECT
    cc.numero,
    cc.droits_et_taxes_douane_hors_tva  AS b_initial,
    cc.tva_importation_douane           AS tva_restante,
    cc.solde_cordon                     AS solde_droits_restant,
    COALESCE(consommes.total_droits, 0) AS droits_consommes,
    CASE
        WHEN COALESCE(consommes.total_droits, 0) > COALESCE(cc.droits_et_taxes_douane_hors_tva, 0)
        THEN 'ALERTE: données historiques incohérentes (old code)'
        ELSE 'OK'
    END AS diagnostic
FROM certificat_credit cc
LEFT JOIN (
    SELECT
        uc.certificat_credit_id,
        SUM(uc.montant_droits) AS total_droits
    FROM utilisation_credit uc
    WHERE uc.type_utilisation = 'DOUANIER'
      AND uc.statut            = 'LIQUIDEE'
    GROUP BY uc.certificat_credit_id
) consommes ON consommes.certificat_credit_id = cc.id
WHERE cc.droits_et_taxes_douane_hors_tva IS NOT NULL
ORDER BY cc.numero;

-- -----------------------------------------------------------------------
--  NOTE IMPORTANTE sur CI-DEMO-SCEN-A
-- -----------------------------------------------------------------------
--  Ce certificat a été liquidé avec l'ANCIEN code qui débitait le montant
--  total (droits+TVA) du solde_cordon au lieu de séparer les deux.
--  Résultat : droits consommés = 3+5+7 = 15 > b=12 (incohérent).
--  Après migration, solde_cordon = GREATEST(0, 12-15) = 0.
--
--  Pour un jeu de données propre, supprimer les utilisations de ce
--  certificat et relancer le DataInitializer (ou recharger une BDD vierge).
-- -----------------------------------------------------------------------
