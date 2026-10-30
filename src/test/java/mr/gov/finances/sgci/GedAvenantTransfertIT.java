package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.DataInitializer;
import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentAvenant;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentAvenantRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AvenantService;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.web.dto.AvenantDto;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.CreateAvenantRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le paramétrage GED gouverne aussi les pièces de l'avenant ({@code MODIFICATION_CI}) et du
 * transfert ({@code TRANSFERT_CREDIT}) : ce que l'administrateur y règle doit avoir un effet, et
 * tenir au redémarrage.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class GedAvenantTransfertIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private AvenantService avenantService;
    @Autowired private AvenantRepository avenantRepository;
    @Autowired private DocumentAvenantRepository documentAvenantRepository;
    @Autowired private DocumentRequirementRepository requirementRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private DataInitializer dataInitializer;
    @Autowired private DocumentRequirementService requirementService;

    // ── l'avenant ──────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unePieceDAvenantRendueObligatoireEstExigeeALaPriseEnCharge() {
        rendreObligatoire(ProcessusDocument.MODIFICATION_CI, "AVENANT_CONTRAT", true);
        AvenantDto a = avenantService.deposer(depot(), compte("entreprise"));

        assertThatThrownBy(() -> avenantService.prendreEnCharge(a.getId(), null, compte("dgtcp")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Documents obligatoires manquants")
                .hasMessageContaining("AVENANT_CONTRAT");

        joindre(a.getId(), "AVENANT_CONTRAT");
        assertThat(avenantService.prendreEnCharge(a.getId(), null, compte("dgtcp")).getStatut())
                .isEqualTo(StatutAvenant.EN_COURS_TRAITEMENT);
    }

    @Test
    @Transactional
    void unePieceDAvenantObligatoireDesactiveeNEstPlusExigee() {
        DocumentRequirement req = rendreObligatoire(ProcessusDocument.MODIFICATION_CI, "AVENANT_CONTRAT", true);
        req.setActif(false);
        requirementRepository.saveAndFlush(req);
        AvenantDto a = avenantService.deposer(depot(), compte("entreprise"));

        assertThat(avenantService.prendreEnCharge(a.getId(), null, compte("dgtcp")).getStatut())
                .isEqualTo(StatutAvenant.EN_COURS_TRAITEMENT);
    }

    // ── la décision de la Commission ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laDecisionDeLaCommissionNEstPasProposeeAuDeposant() {
        assertThat(codes(requirementService.findByProcessus(ProcessusDocument.MODIFICATION_CI, true)))
                .contains("AVENANT_CONTRAT")
                .doesNotContain("DECISION_COMMISSION");
        assertThat(codes(requirementService.findByProcessus(ProcessusDocument.MODIFICATION_CI, false)))
                .as("l'écran de paramétrage la voit toujours")
                .contains("DECISION_COMMISSION");
    }

    @Test
    void chaquePieceDAvenantEstClasseeDeposeeOuProduiteParLaCommission() {
        assertThat(codes(requirementService.findByProcessus(ProcessusDocument.MODIFICATION_CI, false)))
                .as("une pièce non classée serait proposée au déposant sans que rien ne le signale")
                .allMatch(c -> DocumentDepotPolicy.deposees(ProcessusDocument.MODIFICATION_CI).contains(c)
                        || DocumentDepotPolicy.horsDepot(ProcessusDocument.MODIFICATION_CI).contains(c));
    }

    @Test
    @Transactional
    void leDeposantNeJointPasLaDecisionDeLaCommission() {
        AvenantDto a = avenantService.deposer(depot(), compte("entreprise"));

        assertThatCode(() -> avenantService.verifierDepotPiece(a.getId(), "AVENANT_CONTRAT", compte("entreprise")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> avenantService.verifierDepotPiece(a.getId(), "DECISION_COMMISSION", compte("entreprise")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Commission");
    }

    @Test
    @Transactional
    void lePresidentJointLaDecisionUneFoisLAvenantPrisEnCharge() {
        AvenantDto a = avenantService.deposer(depot(), compte("entreprise"));
        assertThatThrownBy(() -> avenantService.verifierDepotPiece(a.getId(), "DECISION_COMMISSION", compte("president")))
                .as("avant l'examen de la DGTCP, il n'y a rien à décider")
                .isInstanceOf(ApiException.class);

        avenantService.prendreEnCharge(a.getId(), null, compte("dgtcp"));

        assertThatCode(() -> avenantService.verifierDepotPiece(a.getId(), "DECISION_COMMISSION", compte("president")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> avenantService.verifierDepotPiece(a.getId(), "AVENANT_CONTRAT", compte("entreprise")))
                .as("les pièces du dépôt sont closes par la prise en charge")
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void uneDecisionObligatoireEstExigeeALaValidationEtNonALaPriseEnCharge() {
        rendreObligatoire(ProcessusDocument.MODIFICATION_CI, "DECISION_COMMISSION", true);
        AvenantDto a = avenantService.deposer(depot(), compte("entreprise"));

        assertThat(avenantService.prendreEnCharge(a.getId(), null, compte("dgtcp")).getStatut())
                .isEqualTo(StatutAvenant.EN_COURS_TRAITEMENT);
        assertThatThrownBy(() -> avenantService.valider(a.getId(), null, compte("president")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("DECISION_COMMISSION");

        joindre(a.getId(), "DECISION_COMMISSION");
        assertThat(avenantService.valider(a.getId(), null, compte("president")).getStatut())
                .isEqualTo(StatutAvenant.ADOPTE);
    }

    // ── le transfert ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneObligationDeTransfertLeveeDansLaGedTientAuRedemarrage() {
        rendreObligatoire(ProcessusDocument.TRANSFERT_CREDIT, "JUSTIFICATIFS_CLOTURE_DOUANE", false);

        // Le seed des pièces, rejoué exactement comme au démarrage du serveur.
        ReflectionTestUtils.invokeMethod(dataInitializer, "seedDocumentRequirements");

        assertThat(requirementRepository
                .findByProcessusAndCodeDocument(ProcessusDocument.TRANSFERT_CREDIT, "JUSTIFICATIFS_CLOTURE_DOUANE")
                .orElseThrow().getObligatoire())
                .as("le démarrage ne doit pas défaire le choix de l'administrateur")
                .isFalse();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private DocumentRequirement rendreObligatoire(ProcessusDocument processus, String code, boolean obligatoire) {
        DocumentRequirement req = requirementRepository.findByProcessusAndCodeDocument(processus, code).orElseThrow();
        req.setObligatoire(obligatoire);
        return requirementRepository.saveAndFlush(req);
    }

    private static java.util.List<String> codes(java.util.List<DocumentRequirementDto> reqs) {
        return reqs.stream().map(DocumentRequirementDto::getCodeDocument).toList();
    }

    private void joindre(Long avenantId, String code) {
        documentAvenantRepository.saveAndFlush(DocumentAvenant.builder()
                .codeDocument(code)
                .nomFichier(code.toLowerCase() + ".pdf")
                .chemin("test/" + code)
                .dateUpload(Instant.now())
                .taille(1L)
                .version(1)
                .actif(true)
                .avenant(avenantRepository.findById(avenantId).orElseThrow())
                .build());
    }

    private CreateAvenantRequest depot() {
        CertificatCredit certificat = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        utilisationRepository.findByCertificatCreditId(certificat.getId()).forEach(u -> {
            u.setStatut(StatutUtilisation.LIQUIDEE);
            utilisationRepository.save(u);
        });
        certificat.setStatut(StatutCertificat.OUVERT);
        certificatRepository.saveAndFlush(certificat);
        return CreateAvenantRequest.builder()
                .certificatCreditId(certificat.getId())
                .type(TypeAvenant.JURIDIQUE)
                .motif("Avenant n°1 au marché")
                .build();
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }
}
