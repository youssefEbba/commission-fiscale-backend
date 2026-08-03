package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.service.ReferenceSequenceGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Backfill de la colonne {@code reference} (format {@code PREFIXE-NN/AAAA}) pour les lignes créées
 * avant l'introduction des références lisibles. Les lignes déjà pourvues sont ignorées ; la numérotation
 * réutilise {@link ReferenceSequenceGenerator} afin de rester cohérente avec les créations futures.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceBackfillMigration {

    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final CertificatCreditRepository certificatCreditRepository;
    private final MarcheRepository marcheRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final ReferenceSequenceGenerator referenceSequenceGenerator;

    @Transactional
    public void migrateIfNeeded() {
        backfill("DemandeCorrection", ReferenceSequenceGenerator.PREFIX_DEMANDE_CORRECTION,
                demandeCorrectionRepository.findAll(),
                DemandeCorrection::getId, DemandeCorrection::getReference,
                DemandeCorrection::setReference, demandeCorrectionRepository::save);

        backfill("CertificatCredit", ReferenceSequenceGenerator.PREFIX_CERTIFICAT,
                certificatCreditRepository.findAll(),
                CertificatCredit::getId, CertificatCredit::getReference,
                CertificatCredit::setReference, certificatCreditRepository::save);

        backfill("Marche", ReferenceSequenceGenerator.PREFIX_MARCHE,
                marcheRepository.findAll(),
                Marche::getId, Marche::getReference,
                Marche::setReference, marcheRepository::save);

        backfill("UtilisationCredit", ReferenceSequenceGenerator.PREFIX_UTILISATION,
                utilisationCreditRepository.findAll(),
                UtilisationCredit::getId, UtilisationCredit::getReference,
                UtilisationCredit::setReference, utilisationCreditRepository::save);
    }

    private <T> void backfill(String entityName, String prefix, List<T> all,
                              Function<T, Long> idGetter,
                              Function<T, String> referenceGetter,
                              BiConsumer<T, String> referenceSetter,
                              java.util.function.Consumer<T> saver) {
        List<T> toBackfill = all.stream()
                .filter(e -> isBlank(referenceGetter.apply(e)))
                .sorted(Comparator.comparing(idGetter, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (toBackfill.isEmpty()) {
            return;
        }
        int count = 0;
        for (T entity : toBackfill) {
            referenceSetter.accept(entity, referenceSequenceGenerator.next(prefix));
            saver.accept(entity);
            count++;
        }
        log.info("{}: {} référence(s) générée(s) par backfill (préfixe {})", entityName, count, prefix);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
