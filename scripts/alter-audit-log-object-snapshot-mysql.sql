-- Élargit object_snapshot (erreur "Data too long for column 'object_snapshot'")
-- À exécuter une fois sur la base MySQL existante si ddl-auto=update n'a pas migré la colonne.

ALTER TABLE audit_log
    MODIFY COLUMN object_snapshot LONGTEXT NULL;
