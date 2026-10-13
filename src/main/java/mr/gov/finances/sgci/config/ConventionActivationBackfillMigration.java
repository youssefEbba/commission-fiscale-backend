package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.repository.ConventionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Déclare actives les conventions antérieures à l'introduction de la disponibilité.
 *
 * <p>La colonne {@code actif} est nullable à dessein : déclarée {@code NOT NULL}, Hibernate l'aurait
 * ajoutée sur une table peuplée et MySQL l'aurait remplie de zéros — les 215 conventions existantes
 * se seraient retrouvées fermées aux nouveaux marchés du jour au lendemain. Le rattrapage se fait
 * donc ici, après le démarrage, comme pour {@code SignatureTypeEmpreinteMigration}.
 *
 * <p>Sans effet sur une base neuve, et idempotent : les conventions créées ensuite portent
 * {@code true} dès la persistance ({@code @Builder.Default} sur l'entité). Le code lit de toute façon
 * {@code null} comme « active » ({@code ConventionDisponibilitePolicy}) : cette migration régularise
 * la donnée, elle ne corrige pas un comportement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(63)
public class ConventionActivationBackfillMigration implements ApplicationRunner {

    private final ConventionRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Convention> sansActivation = repository.findByActifIsNull();
        if (sansActivation.isEmpty()) {
            return;
        }
        sansActivation.forEach(c -> c.setActif(Boolean.TRUE));
        repository.saveAll(sansActivation);
        log.info("Migration conventions : {} convention(s) antérieure(s) déclarée(s) active(s)",
                sansActivation.size());
    }
}
