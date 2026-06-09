-- Migration : référentiel types de documents paramétrables (GED)
-- À exécuter une fois sur MySQL si les tables existent déjà avec type_document (enum/varchar).

CREATE TABLE IF NOT EXISTS referentiel_type_document (
    code VARCHAR(64) NOT NULL PRIMARY KEY,
    libelle VARCHAR(500) NOT NULL,
    libelle_ar VARCHAR(500) NULL,
    actif TINYINT(1) NOT NULL DEFAULT 1,
    systeme TINYINT(1) NOT NULL DEFAULT 0
);

-- document_requirement : type_document -> code_document
ALTER TABLE document_requirement
    ADD COLUMN IF NOT EXISTS code_document VARCHAR(64) NULL AFTER processus;

UPDATE document_requirement
SET code_document = type_document
WHERE type_document IS NOT NULL
  AND (code_document IS NULL OR TRIM(code_document) = '');

-- Supprimer l'ancienne contrainte unique (processus, type_document) si elle existe
-- (sinon le seed au démarrage tente un doublon CONVENTION / CONVENTION_JOIGNED_DOCUMENT)
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'document_requirement'
      AND INDEX_NAME = 'uk_doc_req_process_type'
);
SET @sql_drop = IF(@idx_exists > 0,
    'ALTER TABLE document_requirement DROP INDEX uk_doc_req_process_type',
    'SELECT 1');
PREPARE stmt FROM @sql_drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Nouvelle contrainte (processus, code_document) — ignorer si déjà créée par Hibernate
SET @idx_code_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'document_requirement'
      AND INDEX_NAME = 'uk_doc_req_process_code'
);
SET @sql_add = IF(@idx_code_exists = 0,
    'ALTER TABLE document_requirement ADD CONSTRAINT uk_doc_req_process_code UNIQUE (processus, code_document)',
    'SELECT 1');
PREPARE stmt2 FROM @sql_add;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Optionnel : retirer l'ancienne colonne une fois code_document renseigné partout
-- ALTER TABLE document_requirement DROP COLUMN type_document;

-- Tables document_* : renommer colonne type -> code_document si besoin (exemple) :
-- ALTER TABLE document_utilisation_credit CHANGE COLUMN type code_document VARCHAR(64) NOT NULL;
