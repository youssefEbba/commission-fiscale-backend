package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.reference.FormatReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Convertit les références déjà attribuées au format demandé par l'équipe en octobre 2026 :
 * {@code DC-109-10/2026} devient {@code DC-0109-10-2026}, {@code CU-007/2026} devient
 * {@code CU-0007-2026}.
 *
 * <p>Le numéro garde sa valeur, donc le compteur de chaque préfixe reste juste : la référence
 * suivante ne peut pas entrer en collision avec une référence convertie. Seules les valeurs encore
 * à l'ancien format sont touchées ; rejouée à chaque démarrage, la migration ne fait donc plus rien
 * une fois passée. SQL standard : elle s'exécute aussi sous H2, et les tests l'exercent.
 *
 * <p>Une valeur convertie qui existerait déjà — ce qui supposerait une saisie manuelle — est laissée
 * telle quelle et journalisée, plutôt que de faire échouer le démarrage sur la contrainte d'unicité.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(52)
public class ReferenceFormatMigration implements ApplicationRunner {

    /** Une colonne qui porte une référence lisible. */
    record ColonneReference(String table, String colonne) {
    }

    static final List<ColonneReference> COLONNES = List.of(
            new ColonneReference("demande_correction", "reference"),
            new ColonneReference("certificat_credit", "reference"),
            new ColonneReference("marche", "reference"),
            new ColonneReference("utilisation_credit", "reference"),
            new ColonneReference("utilisation_credit", "numero_certificat_utilisation"),
            new ColonneReference("demande_rattachement", "reference"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        COLONNES.forEach(this::convertir);
    }

    private void convertir(ColonneReference c) {
        try {
            List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
                    "SELECT id, " + c.colonne() + " AS ref FROM " + c.table() + " WHERE " + c.colonne() + " LIKE '%/%'");
            int converties = 0;
            for (Map<String, Object> ligne : lignes) {
                String ancienne = (String) ligne.get("ref");
                if (!FormatReference.estAncienFormat(ancienne)) {
                    continue;
                }
                String nouvelle = FormatReference.convertirAncienFormat(ancienne);
                Integer deja = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + c.table() + " WHERE " + c.colonne() + " = ?", Integer.class, nouvelle);
                if (deja != null && deja > 0) {
                    log.warn("{}.{}: {} non convertie, {} existe déjà", c.table(), c.colonne(), ancienne, nouvelle);
                    continue;
                }
                jdbcTemplate.update("UPDATE " + c.table() + " SET " + c.colonne() + " = ? WHERE id = ?",
                        nouvelle, ligne.get("id"));
                converties++;
            }
            if (converties > 0) {
                log.info("{}.{}: {} référence(s) convertie(s) au nouveau format", c.table(), c.colonne(), converties);
            }
        } catch (Exception e) {
            log.error("{}.{}: conversion des références interrompue : {}", c.table(), c.colonne(), e.getMessage(), e);
        }
    }
}
