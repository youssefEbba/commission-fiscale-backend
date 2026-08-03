package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Supprime les colonnes obsolètes {@code groupement} et {@code chef_de_file_id} de la table
 * {@code entreprise}, résidu de l'ancien modèle (booléen / auto-référence) avant l'entité
 * dédiée {@code Groupement}. Hibernate {@code ddl-auto=update} ne les retire pas tout seul ;
 * leur présence NOT NULL sans défaut bloque tout INSERT sur {@code entreprise}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntrepriseLegacyGroupementMigration {

    private final JdbcTemplate jdbcTemplate;

    public void migrateIfNeeded() {
        dropForeignKeyIfExists("entreprise", "chef_de_file_id");
        dropColumnIfExists("entreprise", "chef_de_file_id");
        dropColumnIfExists("entreprise", "groupement");
    }

    private void dropColumnIfExists(String table, String column) {
        if (!columnExists(table, column)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("{}: colonne obsolète {} supprimée", table, column);
        } catch (Exception e) {
            log.warn("{}: impossible de supprimer la colonne {} — {}", table, column, e.getMessage());
            throw e;
        }
    }

    /**
     * Supprime la FK pointant vers {@code chef_de_file_id} (nom généré par Hibernate variable)
     * avant de dropper la colonne.
     */
    private void dropForeignKeyIfExists(String table, String column) {
        if (!columnExists(table, column)) {
            return;
        }
        try {
            var keys = jdbcTemplate.queryForList("""
                    SELECT CONSTRAINT_NAME
                    FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                      AND REFERENCED_TABLE_NAME IS NOT NULL
                    """, String.class, table, column);
            for (String constraintName : keys) {
                if (constraintName == null || constraintName.isBlank()) {
                    continue;
                }
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
                log.info("{}: contrainte FK {} (colonne {}) supprimée", table, constraintName, column);
            }
        } catch (Exception e) {
            log.warn("{}: impossible de supprimer la FK sur {} — {}", table, column, e.getMessage());
        }
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
}
