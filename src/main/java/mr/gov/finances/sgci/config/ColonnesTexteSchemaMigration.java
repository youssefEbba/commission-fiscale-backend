package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Élargit en {@code VARCHAR} les colonnes texte qui portent une énumération Java.
 *
 * <p>Une colonne {@code ENUM} fige en base la liste des valeurs connues le jour de sa création.
 * {@code ddl-auto=update} n'y ajoute pas les valeurs apparues depuis : Hibernate compare les
 * <em>types</em>, pas les listes de valeurs. MySQL tronque alors la valeur inconnue et lève
 * « Data truncated for column … », au premier clic de l'utilisateur sur la fonctionnalité neuve —
 * jamais en test, où le schéma est recréé à chaque exécution.
 *
 * <p>La réponse est de ne pas laisser la base arbitrer une liste de valeurs qu'elle ne connaîtra
 * jamais complètement : la colonne devient un {@code VARCHAR} de la longueur déclarée sur l'entité,
 * et la contrainte de valeurs reste là où elle est vraiment exprimée — l'énumération Java et, pour
 * les statuts, le graphe de transitions.
 *
 * <p>Idempotent et sans effet sur une base déjà conforme : une colonne déjà {@code VARCHAR} assez
 * longue n'est pas touchée. S'exécute avant toutes les autres migrations ({@code @Order(50)}) car
 * celles qui écrivent des lignes — {@code UtilisationDouaneViseMigration} et les suivantes —
 * réécrivent la ligne entière, colonne de statut comprise, et échoueraient sur une colonne courte.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(50)
public class ColonnesTexteSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateIfNeeded();
    }

    /**
     * Le registre des colonnes concernées.
     *
     * <p>La longueur est toujours celle déclarée par l'entité sur le champ correspondant — 64 pour
     * {@code Notification.type}, 32 pour {@code UtilisationCredit.statut}. C'est une donnée, et non
     * une suite d'appels, pour que {@code ColonnesTexteSchemaMigrationTest} puisse la relire et
     * vérifier qu'aucune valeur d'énumération n'y dépasse la longueur annoncée.
     */
    static final List<ColonneTexte> REGISTRE = List.of(
            new ColonneTexte("notification", "type", 64, true),
            new ColonneTexte("notification", "entity_type", 64, true),
            new ColonneTexte("utilisation_credit", "statut", 32, true));

    public void migrateIfNeeded() {
        REGISTRE.forEach(this::widenStringColumnIfNeeded);
    }

    /** Une colonne texte à maintenir à la longueur que son entité déclare. */
    record ColonneTexte(String table, String colonne, int longueur, boolean notNull) {
    }

    /**
     * La sonde et l'instruction émise sont toutes deux propres à MySQL : {@code DATA_TYPE} y vaut
     * {@code varchar}, là où H2 — la base des tests — répond {@code CHARACTER VARYING}. Sur H2 la
     * migration ne fait donc rien, ce qui est sans conséquence : le schéma de test est régénéré
     * depuis les entités à chaque exécution, donc déjà conforme.
     */
    private void widenStringColumnIfNeeded(ColonneTexte c) {
        widenStringColumnIfNeeded(c.table(), c.colonne(), c.longueur(), c.notNull());
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

    record ColumnInfo(String dataType, Integer maxLength) {
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
