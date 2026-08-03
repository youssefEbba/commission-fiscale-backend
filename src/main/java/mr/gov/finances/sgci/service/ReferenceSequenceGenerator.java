package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ReferenceSequence;
import mr.gov.finances.sgci.repository.ReferenceSequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;

/**
 * Génère des références lisibles séquentielles par préfixe et par année. La numérotation repose sur une
 * table de séquence dédiée verrouillée en écriture, ce qui évite les collisions en cas de créations
 * concurrentes.
 *
 * <p>Format unique pour tous les préfixes : {@code PREFIXE-NNN-MM/AAAA} (ex. {@code DC-001-01/2025}).
 *
 * <p>Préfixes conventionnels :
 * <ul>
 *   <li>{@code DC} — Demande de correction</li>
 *   <li>{@code CR} — Certificat de crédit</li>
 *   <li>{@code DM} — Marché (mise en place)</li>
 *   <li>{@code DU} — Utilisation de crédit</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReferenceSequenceGenerator {

    public static final String PREFIX_DEMANDE_CORRECTION = "DC";
    public static final String PREFIX_CERTIFICAT = "CR";
    public static final String PREFIX_MARCHE = "DM";
    public static final String PREFIX_UTILISATION = "DU";

    private final ReferenceSequenceRepository repository;

    /**
     * Génère la prochaine référence pour l'année courante.
     * Point d'entrée transactionnel (appelé via le proxy Spring) — la surcharge à 2 arguments
     * est invoquée en interne et hérite de cette transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix) {
        LocalDate now = LocalDate.now();
        return doNext(prefix, now.getYear(), now.getMonthValue());
    }

    /**
     * Génère la prochaine référence pour l'année indiquée (le mois reste celui du jour de génération).
     * Exécuté dans une transaction dédiée ({@code REQUIRES_NEW}) pour que le compteur soit committé
     * même si la transaction appelante échoue, garantissant l'unicité (au prix de « trous » possibles,
     * acceptables pour une référence lisible).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix, int year) {
        return doNext(prefix, year, LocalDate.now().getMonthValue());
    }

    private String doNext(String prefix, int year, int month) {
        String key = prefix + "-" + year;
        ReferenceSequence sequence = repository.findByKeyForUpdate(key)
                .orElseGet(() -> createSequence(key));
        long value = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(value);
        repository.saveAndFlush(sequence);
        return format(prefix, value, month, year);
    }

    private ReferenceSequence createSequence(String key) {
        try {
            return repository.saveAndFlush(ReferenceSequence.builder()
                    .sequenceKey(key)
                    .currentValue(0L)
                    .build());
        } catch (DataIntegrityViolationException concurrentInsert) {
            return repository.findByKeyForUpdate(key)
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private String format(String prefix, long value, int month, int year) {
        return String.format("%s-%03d-%02d/%d", prefix, value, month, year);
    }
}
