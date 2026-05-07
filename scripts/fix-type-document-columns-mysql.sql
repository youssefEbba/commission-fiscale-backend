-- ============================================================
-- Correction : colonne 'type' trop courte dans toutes les tables de documents
-- Erreur : Data truncated for column 'type'
-- Cause : TypeDocument contient des valeurs longues (ex. CHEQUE_CERTIFIE = 15 chars,
--         LETTRE_NOTIFICATION_CONTRAT = 28 chars, CERTIFICAT_CREDIT_IMPOTS_SYDONIA = 32 chars)
--         Les colonnes étaient au défaut VARCHAR(25 ou 31) créées par Hibernate.
-- Solution : agrandir toutes les colonnes 'type' à VARCHAR(64)
-- ============================================================

ALTER TABLE document_utilisation_credit MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document_transfert_credit    MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document_cloture_credit      MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document_certificat_credit   MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document_avenant             MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document_sous_traitance      MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE document                     MODIFY COLUMN type VARCHAR(64) NOT NULL;

-- Vérification
SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME  = 'type'
  AND TABLE_NAME IN (
      'document_utilisation_credit',
      'document_transfert_credit',
      'document_cloture_credit',
      'document_certificat_credit',
      'document_avenant',
      'document_sous_traitance',
      'document'
  )
ORDER BY TABLE_NAME;
