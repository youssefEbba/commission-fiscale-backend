package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfill {@code code_document} depuis l'ancienne colonne {@code type_document}
 * et suppression de l'index unique legacy {@code uk_doc_req_process_type}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRequirementLegacyMigration {

    private final JdbcTemplate jdbcTemplate;

    public void migrateIfNeeded() {
        if (!columnExists("document_requirement", "type_document")) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE document_requirement
                SET code_document = type_document
                WHERE type_document IS NOT NULL
                  AND (code_document IS NULL OR TRIM(code_document) = '')
                """);
        if (updated > 0) {
            log.info("document_requirement: {} ligne(s) — code_document renseigné depuis type_document", updated);
        }
        dropIndexIfExists("document_requirement", "uk_doc_req_process_type");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private void dropIndexIfExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, table, indexName);
        if (count == null || count == 0) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP INDEX " + indexName);
            log.info("document_requirement: index {} supprimé", indexName);
        } catch (Exception e) {
            log.warn("document_requirement: impossible de supprimer l'index {} — {}", indexName, e.getMessage());
        }
    }
}
