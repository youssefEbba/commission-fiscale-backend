package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AvenantService;
import mr.gov.finances.sgci.service.DocumentCertificatCreditService;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.TransfertCreditService;
import mr.gov.finances.sgci.web.dto.AvenantDto;
import mr.gov.finances.sgci.web.dto.CreateAvenantRequest;
import mr.gov.finances.sgci.web.dto.CreateTransfertCreditRequest;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.TransfertCreditDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le récépissé de dépôt existe pour toutes les demandes : mise en place, utilisations, transferts,
 * avenants et sous-traitance, en plus de la correction ({@link RecuDepotDemandeCorrectionIT}).
 *
 * <p>Il n'est jamais proposé dans le formulaire de dépôt, mais le déposant le dépose lui-même après
 * chaque soumission, et le remplace sans rejet ouvert (l'ancien passe en historique).
 */
@SpringBootTest
@ActiveProfiles("test")
class RecuDepotTousProcessusIT {

    private static final String RECU = DocumentDepotPolicy.RECU_DEPOT;
    private static final List<ProcessusDocument> PROCESSUS = List.of(ProcessusDocument.MISE_EN_PLACE_CI,
            ProcessusDocument.UTILISATION_CI_DOUANE, ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE,
            ProcessusDocument.TRANSFERT_CREDIT, ProcessusDocument.MODIFICATION_CI, ProcessusDocument.SOUS_TRAITANCE);

    @Autowired private DocumentRequirementRepository requirementRepository;
    @Autowired private DocumentRequirementService requirementService;
    @Autowired private DocumentCertificatCreditService documentCertificatService;
    @Autowired private DocumentUtilisationCreditService documentUtilisationService;
    @Autowired private AvenantService avenantService;
    @Autowired private TransfertCreditService transfertService;
    @Autowired private TransfertCreditRepository transfertRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    void chaqueProcessusAUnRecepisseFacultatifHorsDuFormulaireDeDepot() {
        for (ProcessusDocument p : PROCESSUS) {
            var req = requirementRepository.findByProcessusAndCodeDocument(p, RECU);
            assertThat(req).as(p.name()).isPresent();
            assertThat(req.get().getObligatoire()).as(p.name()).isFalse();
            assertThat(DocumentDepotPolicy.deposableParLeDemandeur(p, RECU)).as(p.name()).isFalse();
            assertThat(requirementService.findByProcessus(p, true))
                    .extracting(DocumentRequirementDto::getCodeDocument).as(p.name()).doesNotContain(RECU);
        }
    }

    @Test
    @Transactional
    void lAutoriteContractanteDeposeEtRedeposeLeRecepisseDeMiseEnPlace() throws Exception {
        CertificatCredit c = certificatRepository.findAll().stream()
                .filter(x -> x.getStatut() != StatutCertificat.INCOMPLETE)
                .findFirst().orElseThrow();
        AuthenticatedUser ac = compte("ac");

        assertThat(documentCertificatService.upload(c.getId(), RECU, null, pdf(), null, ac).getVersion()).isEqualTo(1);
        assertThat(documentCertificatService.upload(c.getId(), RECU, null, pdf(), null, ac).getVersion()).isEqualTo(2);
        // L'exemption reste étroite : le certificat signé reste la pièce du Président.
        assertThatThrownBy(() -> documentCertificatService.upload(c.getId(), "CERTIFICAT_CREDIT_IMPOTS", null, pdf(), null, ac))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void lEntrepriseDeposeEtRedeposeLeRecepisseDUtilisation() throws Exception {
        UtilisationCredit u = utilisationRepository.findAll().stream()
                .filter(x -> x.getStatut() != StatutUtilisation.INCOMPLETE && x.getStatut() != StatutUtilisation.BROUILLON)
                .filter(x -> x.getType() == TypeUtilisation.DOUANIER)
                .findFirst().orElseThrow();
        AuthenticatedUser entreprise = compte("entreprise");

        assertThat(documentUtilisationService.upload(u.getId(), RECU, null, pdf(), null, entreprise).getVersion()).isEqualTo(1);
        assertThat(documentUtilisationService.upload(u.getId(), RECU, null, pdf(), null, entreprise).getVersion()).isEqualTo(2);
        assertThatThrownBy(() -> documentUtilisationService.upload(u.getId(), "BULLETIN_ANNOTE", null, pdf(), null, entreprise))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void leDeposantJointLeRecepisseDeSonAvenant() {
        CertificatCredit certificat = certificatRepository.findByNumero("CI-DEMO-SCEN-E").orElseThrow();
        utilisationRepository.findByCertificatCreditId(certificat.getId()).forEach(u -> {
            u.setStatut(StatutUtilisation.LIQUIDEE);
            utilisationRepository.save(u);
        });
        certificat.setStatut(StatutCertificat.OUVERT);
        certificatRepository.saveAndFlush(certificat);
        AvenantDto a = avenantService.deposer(CreateAvenantRequest.builder()
                .certificatCreditId(certificat.getId()).type(TypeAvenant.JURIDIQUE).motif("Avenant n°1").build(),
                compte("entreprise"));

        assertThatCode(() -> avenantService.verifierDepotPiece(a.getId(), RECU, compte("entreprise")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> avenantService.verifierDepotPiece(a.getId(), "DECISION_COMMISSION", compte("entreprise")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void unTransfertRecoitUneReferenceTR() {
        AuthenticatedUser entreprise = compte("entreprise");
        Long entrepriseId = utilisateurRepository.findByUsername("entreprise").orElseThrow().getEntreprise().getId();
        CertificatCredit c = certificatRepository.findAll().stream()
                .filter(x -> x.getEntreprise() != null && entrepriseId.equals(x.getEntreprise().getId()))
                .filter(x -> x.getStatut() == StatutCertificat.OUVERT || x.getStatut() == StatutCertificat.MODIFIE)
                .filter(x -> transfertRepository.findByCertificatCreditId(x.getId()).isEmpty())
                .findFirst().orElseThrow(() -> new IllegalStateException("Aucun crédit ouvert sans transfert"));

        TransfertCreditDto t = transfertService.create(CreateTransfertCreditRequest.builder()
                .certificatCreditId(c.getId()).operationsDouaneCloturees(true).build(), entreprise);

        assertThat(t.getReference()).matches("TR-\\d{4}-\\d{2}-\\d{4}");
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private static MultipartFile pdf() {
        return new MockMultipartFile("file", "recepisse.pdf", "application/pdf", "%PDF-1.4 recepisse".getBytes());
    }
}
