package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dispense de l'émission présidentielle les dossiers déjà liquidés ou apurés.
 *
 * <p>L'émission du certificat d'utilisation par le Président est une étape nouvelle. Les dossiers
 * parvenus à {@code LIQUIDEE} ou {@code APUREE} avant son introduction ne l'ont jamais traversée :
 * leur réclamer un acte présidentiel rétroactif inscrirait en base une décision qui n'a pas eu lieu,
 * et les bloquer à la clôture serait une régression sur des dossiers que rien ne distingue par
 * ailleurs. La base de production en comptait 107, liquidés ou apurés entre 2023 et 2025.
 *
 * <p><b>Auto-limité, et c'est le point délicat.</b> Le marqueur est posé à {@code TRUE} par le calcul
 * de la DGTCP ({@code liquiderDouane}, {@code apurerTVAInterieure}) : tout dossier instruit après ce
 * déploiement arrive donc avec une valeur non nulle, et ce rattrapage ne le voit pas. Sans cette
 * précaution, il dispenserait à chaque redémarrage les dossiers légitimement en attente d'émission —
 * exactement ce que l'étape cherche à empêcher.
 *
 * <p>Ne change aucun statut et ne numérote rien.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(62)
public class CertificatUtilisationGrandfatheringMigration implements ApplicationRunner {

    private final UtilisationCreditRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<UtilisationCredit> historiques = repository.findAll().stream()
                .filter(u -> u.getEmissionCertificatRequise() == null)
                .filter(u -> u.getStatut() == StatutUtilisation.LIQUIDEE
                        || u.getStatut() == StatutUtilisation.APUREE)
                .toList();

        if (historiques.isEmpty()) {
            return;
        }
        historiques.forEach(u -> u.setEmissionCertificatRequise(Boolean.FALSE));
        repository.saveAll(historiques);
        log.info("Certificat d'utilisation : {} dossier(s) déjà liquidé(s) ou apuré(s) dispensé(s) de "
                + "l'émission présidentielle (antérieurs à l'introduction de l'étape)", historiques.size());
    }
}
