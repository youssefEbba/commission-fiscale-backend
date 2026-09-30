package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.Signature;
import mr.gov.finances.sgci.domain.enums.TypeEmpreinte;
import mr.gov.finances.sgci.repository.SignatureRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Qualifie en {@code SIGNATURE} les empreintes antérieures à l'introduction du cachet.
 *
 * <p>La colonne {@code type_empreinte} est délibérément nullable : la déclarer {@code NOT NULL}
 * aurait conduit Hibernate à l'ajouter ainsi sur une table déjà peuplée, MySQL l'aurait remplie de
 * chaînes vides, et la lecture suivante aurait échoué sur {@code No enum constant} pour
 * <em>toutes</em> les signatures existantes. Le rattrapage se fait donc ici, après le démarrage.
 *
 * <p>Sans effet sur une base neuve, et idempotent : les lignes créées ensuite portent leur type dès
 * la persistance ({@code @Builder.Default} et {@code @PrePersist} sur l'entité).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(61)
public class SignatureTypeEmpreinteMigration implements ApplicationRunner {

    private final SignatureRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Signature> sansType = repository.findByTypeIsNull();
        if (sansType.isEmpty()) {
            return;
        }
        sansType.forEach(s -> s.setType(TypeEmpreinte.SIGNATURE));
        repository.saveAll(sansType);
        log.info("Migration empreintes : {} signature(s) antérieure(s) au cachet qualifiée(s) en SIGNATURE",
                sansType.size());
    }
}
