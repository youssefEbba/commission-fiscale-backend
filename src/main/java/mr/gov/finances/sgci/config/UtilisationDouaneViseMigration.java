package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.DecisionUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Débloque les demandes d'utilisation douanière restées en {@code EN_CONTROLE_DGD}.
 *
 * <p>Avant correction, le visa de la DGD laissait le dossier au statut de contrôle : l'entreprise
 * n'avait alors aucun moyen de saisir le chèque, et le dossier ne pouvait plus avancer. Les
 * dossiers déjà visés sont replacés en {@code VISE}, statut depuis lequel la suite du circuit
 * redevient accessible.
 *
 * <p>Ne touche que les dossiers portant une décision de visa de la DGD : ceux encore réellement en
 * cours de contrôle restent où ils sont.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(60)
public class UtilisationDouaneViseMigration implements ApplicationRunner {

    private final UtilisationCreditRepository utilisationRepository;
    private final DecisionUtilisationCreditRepository decisionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<UtilisationCredit> bloquees = utilisationRepository.findAll().stream()
                .filter(u -> u.getType() == TypeUtilisation.DOUANIER)
                .filter(u -> u.getStatut() == StatutUtilisation.EN_CONTROLE_DGD)
                .filter(u -> decisionRepository.existsByUtilisationCreditIdAndRoleAndDecision(
                        u.getId(), Role.DGD, DecisionCorrectionType.VISA))
                .toList();

        if (bloquees.isEmpty()) {
            return;
        }
        bloquees.forEach(u -> u.setStatut(StatutUtilisation.VISE));
        utilisationRepository.saveAll(bloquees);
        log.info("Migration P4 : {} demande(s) d'utilisation douanière visées par la DGD "
                + "replacées de EN_CONTROLE_DGD vers VISE", bloquees.size());
    }
}
