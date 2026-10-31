package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.service.DemandeRattachementService;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.web.dto.CreateDemandeRattachementRequest;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Les pièces de la page d'inscription suivent le paramétrage de la GED.
 *
 * <p>Une pièce désactivée y restait proposée — puis son dépôt était refusé, « ce document n'est plus
 * demandé » — et une pièce obligatoire désactivée bloquait toute inscription, faute de pouvoir la
 * joindre.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class PiecesInscriptionGedIT {

    private static final String MANDAT = "MANDAT_REPRESENTATION";

    @Autowired private DemandeRattachementService service;
    @Autowired private DocumentRequirementService requirementService;
    @Autowired private DocumentRequirementRepository requirementRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;

    @Test
    @Transactional
    void unePieceDesactiveeNEstPlusProposee() {
        desactiver(MANDAT);

        assertThat(service.piecesAttendues())
                .extracting(DocumentRequirementDto::getCodeDocument)
                .doesNotContain(MANDAT);
    }

    @Test
    @Transactional
    void unePieceObligatoireDesactiveeNEstPlusExigee() {
        desactiver(MANDAT);

        assertThatCode(() -> service.deposer(inscription(), Map.of())).doesNotThrowAnyException();
    }

    private void desactiver(String code) {
        DocumentRequirement req = requirementRepository
                .findByProcessusAndCodeDocument(ProcessusDocument.RATTACHEMENT_COMPTE, code).orElseThrow();
        assertThat(req.getObligatoire()).as("le mandat est la pièce obligatoire du seed").isTrue();
        requirementService.changerActivation(req.getId(), false);
    }

    private CreateDemandeRattachementRequest inscription() {
        Long entrepriseId = entrepriseRepository.findByNif("NIF_TEST").orElseThrow().getId();
        return CreateDemandeRattachementRequest.builder()
                .username("inscrit-" + System.nanoTime())
                .password("MotDePasse!2026")
                .nomComplet("Personne à rattacher")
                .email("inscrit@example.mr")
                .roleDemande(Role.ENTREPRISE)
                .typeEntite(TypeEntiteRattachement.ENTREPRISE)
                .entiteId(entrepriseId)
                .build();
    }
}
