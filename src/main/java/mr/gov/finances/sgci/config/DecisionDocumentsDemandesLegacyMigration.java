package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Remet d'aplomb les tables des pièces demandées par un rejet temporaire.
 *
 * <p>Jusqu'en juin 2026, la pièce y était une colonne ENUM {@code type_document}, membre de la clé
 * primaire. Le passage à {@code code_document} l'a laissée en place : {@code ddl-auto=update}
 * ajoute, mais ne retire ni ne reconstruit rien. Chaque nouvelle ligne y reçoit donc la même valeur
 * par défaut, et dès qu'un rejet réclame deux pièces la clé {@code (décision, type_document)} est
 * en double — le « Conflit de données » que les agents ont relevé, alors qu'une seule pièce passe.
 *
 * <p>Pour chaque table encore porteuse de la colonne : les anciennes lignes, dont le code était
 * resté vide, le reprennent de {@code type_document} ; puis la colonne est retirée et la clé
 * reconstruite sur {@code (décision, code_document)}. Sans effet sur un schéma déjà conforme, et
 * sur H2, où les tests recréent le schéma depuis les entités : les sondes visent la base courante
 * MySQL. Un échec est journalisé sans bloquer le démarrage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(51)
public class DecisionDocumentsDemandesLegacyMigration implements ApplicationRunner {

    private static final String COLONNE_HERITEE = "type_document";
    private static final String COLONNE_CODE = "code_document";

    /** Une table de pièces demandées, et la colonne qui la rattache à sa décision. */
    record TableCollection(String table, String proprietaire) {
    }

    static final List<TableCollection> TABLES = List.of(
            new TableCollection("decision_correction_documents_demandes", "decision_correction_id"),
            new TableCollection("decision_certificat_credit_documents_demandes", "decision_certificat_credit_id"),
            new TableCollection("decision_utilisation_credit_documents_demandes", "decision_utilisation_credit_id"),
            new TableCollection("decision_transfert_credit_documents_demandes", "decision_transfert_credit_id"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        TABLES.forEach(this::migrerSiBesoin);
    }

    private void migrerSiBesoin(TableCollection t) {
        try {
            // Absente de la base courante : table jamais créée, ou H2, dont DATABASE() ne désigne pas
            // le schéma des tables. Dans les deux cas il n'y a rien à corriger.
            if (!colonneExiste(t.table(), COLONNE_CODE)) {
                return;
            }
            boolean heritee = colonneExiste(t.table(), COLONNE_HERITEE);
            String sql = instruction(t.table(), t.proprietaire(), clePrimaire(t.table()), heritee);
            if (sql == null) {
                return;
            }
            if (heritee) {
                int reprises = jdbcTemplate.update("UPDATE " + t.table() + " SET " + COLONNE_CODE + " = " + COLONNE_HERITEE
                        + " WHERE (" + COLONNE_CODE + " IS NULL OR " + COLONNE_CODE + " = '') AND " + COLONNE_HERITEE + " IS NOT NULL");
                log.info("{}: {} pièce(s) reprise(s) de {}", t.table(), reprises, COLONNE_HERITEE);
            }
            jdbcTemplate.execute(sql);
            log.info("{}: schéma hérité corrigé ({})", t.table(), sql);
        } catch (Exception e) {
            log.error("{}: schéma hérité non corrigé — les rejets à plusieurs pièces resteront en échec : {}",
                    t.table(), e.getMessage(), e);
        }
    }

    /**
     * L'instruction qui rend la table conforme, {@code null} si elle l'est déjà.
     *
     * <p>Une seule instruction : la nouvelle clé primaire sert aussi d'index à la clé étrangère vers
     * la décision, que MySQL refuserait de laisser sans index entre deux instructions.
     */
    static String instruction(String table, String proprietaire, List<String> clePrimaire, boolean colonneHeritee) {
        List<String> cible = List.of(proprietaire, COLONNE_CODE);
        boolean cleConforme = cible.equals(clePrimaire);
        List<String> parties = new ArrayList<>();
        if (!cleConforme && !clePrimaire.isEmpty()) {
            parties.add("DROP PRIMARY KEY");
        }
        if (colonneHeritee) {
            parties.add("DROP COLUMN " + COLONNE_HERITEE);
        }
        if (!cleConforme) {
            parties.add("ADD PRIMARY KEY (" + proprietaire + ", " + COLONNE_CODE + ")");
        }
        return parties.isEmpty() ? null : "ALTER TABLE " + table + " " + String.join(", ", parties);
    }

    private boolean colonneExiste(String table, String colonne) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, colonne);
        return n != null && n > 0;
    }

    /** Les colonnes de la clé primaire, dans l'ordre ; vide si la table n'en a pas ou n'existe pas. */
    private List<String> clePrimaire(String table) {
        return jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'
                ORDER BY ORDINAL_POSITION
                """, String.class, table);
    }
}
