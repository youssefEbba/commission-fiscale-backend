package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Élargit {@code notification.type} (ENUM ou VARCHAR trop court) pour accepter
 * PASSWORD_RESET_REQUEST, DEMANDE_EXPLICATION, etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    public void migrateIfNeeded() {
        widenStringColumnIfNeeded("notification", "type", 64, true);
        widenStringColumnIfNeeded("notification", "entity_type", 64, true);
    }

    private void widenStringColumnIfNeeded(String table, String column, int minLength, boolean notNull) {
        ColumnInfo info = loadColumnInfo(table, column);
        if (info == null) {
            return;
        }
        if (!info.needsWiden(minLength)) {
            return;
        }
        String nullClause = notNull ? " NOT NULL" : " NULL";
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column
                    + " VARCHAR(" + minLength + ")" + nullClause);
            log.info("{}: colonne {} migrée vers VARCHAR({})", table, column, minLength);
        } catch (Exception e) {
            log.warn("{}: impossible de migrer la colonne {} — {}", table, column, e.getMessage());
        }
    }

    private ColumnInfo loadColumnInfo(String table, String column) {
        return jdbcTemplate.query("""
                        SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                rs -> rs.next()
                        ? new ColumnInfo(rs.getString("DATA_TYPE"), rs.getObject("CHARACTER_MAXIMUM_LENGTH", Integer.class))
                        : null,
                table, column);
    }

    private record ColumnInfo(String dataType, Integer maxLength) {
        boolean needsWiden(int minLength) {
            if (dataType == null) {
                return false;
            }
            if ("enum".equalsIgnoreCase(dataType)) {
                return true;
            }
            if ("varchar".equalsIgnoreCase(dataType) || "char".equalsIgnoreCase(dataType)) {
                return maxLength == null || maxLength < minLength;
            }
            return false;
        }
    }
}
