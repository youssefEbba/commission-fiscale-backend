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
 * Relie l'existant aux nouvelles tables de liaison, à partir des colonnes principales.
 *
 * <p>Depuis octobre 2026, une convention relie plusieurs autorités et plusieurs bailleurs, et un
 * marché plusieurs conventions. Les colonnes d'origine ({@code convention.autorite_contractante_id},
 * {@code convention.bailleur_id}, {@code marche.convention_id}) restent celles de l'élément
 * <em>principal</em> ; les tables {@code convention_autorite}, {@code convention_bailleur} et
 * {@code marche_convention} portent tous les liens. Les enregistrements créés depuis s'y inscrivent
 * d'eux-mêmes ({@code @PrePersist}) ; ceux d'avant y sont inscrits ici. Le marché reçoit aussi
 * l'autorité qui le porte : celle de sa convention principale.
 *
 * <p>SQL standard et sans effet une fois passée : chaque insertion vérifie l'absence de la ligne.
 * Un échec est journalisé sans bloquer le démarrage — le code lit les colonnes principales en plus
 * des tables, rien n'est perdu en attendant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(53)
public class ConventionMultiLiensMigration implements ApplicationRunner {

    static final List<String> INSTRUCTIONS = List.of(
            "INSERT INTO convention_autorite (convention_id, autorite_contractante_id) "
                    + "SELECT c.id, c.autorite_contractante_id FROM convention c "
                    + "WHERE c.autorite_contractante_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM convention_autorite x "
                    + "WHERE x.convention_id = c.id AND x.autorite_contractante_id = c.autorite_contractante_id)",
            "INSERT INTO convention_bailleur (convention_id, bailleur_id) "
                    + "SELECT c.id, c.bailleur_id FROM convention c "
                    + "WHERE c.bailleur_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM convention_bailleur x "
                    + "WHERE x.convention_id = c.id AND x.bailleur_id = c.bailleur_id)",
            "INSERT INTO marche_convention (marche_id, convention_id) "
                    + "SELECT m.id, m.convention_id FROM marche m "
                    + "WHERE m.convention_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM marche_convention x "
                    + "WHERE x.marche_id = m.id AND x.convention_id = m.convention_id)",
            "UPDATE marche SET autorite_contractante_id = (SELECT c.autorite_contractante_id FROM convention c "
                    + "WHERE c.id = marche.convention_id) "
                    + "WHERE autorite_contractante_id IS NULL AND convention_id IS NOT NULL");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : INSTRUCTIONS) {
            try {
                int n = jdbcTemplate.update(sql);
                if (n > 0) {
                    log.info("Liens multiples : {} ligne(s) reprise(s) — {}", n, sql.substring(0, sql.indexOf(' ', 12)));
                }
            } catch (Exception e) {
                log.error("Liens multiples : reprise interrompue ({}) : {}", sql, e.getMessage(), e);
            }
        }
    }
}
