-- ─────────────────────────────────────────────────────────────────────────────
-- Diagnostic : état du transfert et des soldes TVA
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Vérifier la structure de la colonne statut
SHOW COLUMNS FROM transfert_credit LIKE 'statut';
-- Résultat attendu : varchar(32)
-- Si ENUM ou varchar trop court → exécuter fix-transfert-credit-statut-mysql.sql

-- 2. Soldes du certificat CI-DEMO-SCEN-B
SELECT
    id,
    numero,
    tva_importation_douane            AS "TVA import restante",
    tva_importation_douane_accordee   AS "TVA import accordée (initiale)",
    solde_cordon                      AS "Solde cordon",
    solde_tva                         AS "Solde TVA intérieure"
FROM certificat_credit
WHERE numero = 'CI-DEMO-SCEN-B';

-- 3. Liquidations douanières qui ont consommé le quota TVA import
SELECT
    uc.id,
    uc.statut,
    uc.date_liquidation,
    ud.montant_tva         AS "TVA consommée (AU_CI)",
    ud.total_pris_en_charge,
    ud.total_a_payer
FROM utilisation_credit uc
JOIN utilisation_douaniere_ext ud ON ud.id = uc.id   -- même table (SINGLE_TABLE)
WHERE uc.certificat_credit_id = (
    SELECT id FROM certificat_credit WHERE numero = 'CI-DEMO-SCEN-B'
)
AND uc.statut IN ('LIQUIDEE', 'VISE');

-- Requête simplifiée si la précédente ne fonctionne pas (SINGLE_TABLE inheritance) :
SELECT
    id,
    statut,
    date_liquidation,
    montant_tva,
    total_pris_en_charge,
    total_a_payer
FROM utilisation_credit
WHERE certificat_credit_id = (
    SELECT id FROM certificat_credit WHERE numero = 'CI-DEMO-SCEN-B'
)
AND type_utilisation = 'DOUANIER'
AND statut IN ('LIQUIDEE', 'VISE');

-- 4. Stock TVA déductible actuel pour ce certificat
SELECT
    s.id,
    s.montant_initial,
    s.montant_restant,
    s.montant_initial - s.montant_restant AS "montant consommé",
    s.date_creation,
    s.utilisation_douane_id   -- NULL = origine transfert de crédit
FROM tva_deductible_stock s
WHERE s.certificat_credit_id = (
    SELECT id FROM certificat_credit WHERE numero = 'CI-DEMO-SCEN-B'
)
ORDER BY s.date_creation;
