package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le formulaire de dépôt d'une utilisation ne réclame pas à l'entreprise les pièces des étapes du
 * circuit — et elle ne peut pas les déposer elle-même.
 *
 * <p>L'équipe de test l'a relevé : le dépôt proposait le certificat d'utilisation, le bulletin annoté,
 * le chèque certifié et la quittance du Trésor. Le manuel n'en liste aucune au dépôt. Le bulletin
 * annoté déposé par l'entreprise suffisait en outre à franchir le contrôle « bulletin au dossier » du
 * visa DGD et de la transmission au Président.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class PiecesDepotUtilisationIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";
    private static final List<ProcessusDocument> UTILISATIONS = List.of(
            ProcessusDocument.UTILISATION_CI_DOUANE, ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE);

    @Autowired private DocumentRequirementService requirementService;
    @Autowired private DocumentRequirementRepository requirementRepository;
    @Autowired private DocumentUtilisationCreditService documentService;
    @Autowired private DocumentUtilisationCreditRepository documentRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser president;

    @BeforeEach
    void setUp() {
        Utilisateur ent = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise = new AuthenticatedUser(ent.getId(), ent.getUsername(), ent.getRole());
        Utilisateur pres = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(pres.getId(), pres.getUsername(), pres.getRole());
    }

    // ── le garde-fou contre l'oubli ────────────────────────────────────────────────────────────

    @Test
    void toutCodeParametreEstClasse() {
        for (ProcessusDocument processus : UTILISATIONS) {
            List<String> codes = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus).stream()
                    .map(DocumentRequirement::getCodeDocument)
                    .distinct()
                    .collect(Collectors.toList());

            assertThat(codes).as(processus.name()).isNotEmpty();
            for (String code : codes) {
                boolean classe = DocumentDepotPolicy.deposees(processus).contains(code)
                        || DocumentDepotPolicy.horsDepot(processus).contains(code);
                assertThat(classe)
                        .as("Le code « %s » est paramétré sur %s mais n'est classé ni déposé ni hors "
                                + "dépôt : il serait réclamé au demandeur par défaut", code, processus)
                        .isTrue();
            }
        }
    }

    @Test
    void lesDeuxFamillesSontDisjointes() {
        assertThat(DocumentDepotPolicy.UTILISATION_CODES_DEPOSES)
                .doesNotContainAnyElementsOf(DocumentDepotPolicy.UTILISATION_CODES_DU_CIRCUIT);
    }

    // ── le formulaire de dépôt ─────────────────────────────────────────────────────────────────

    @Test
    void leDepotEnDouaneNeProposePasLesPiecesDuCircuit() {
        List<String> codes = codesAuDepot(ProcessusDocument.UTILISATION_CI_DOUANE);

        assertThat(codes).contains("DECLARATION_DOUANE", "BULLETIN_LIQUIDATION", "FACTURE");
        assertThat(codes).doesNotContain("CERTIFICAT_UTILISATION", "BULLETIN_ANNOTE",
                "CHEQUE_CERTIFIE", "QUITTANCE_TRESOR");
    }

    @Test
    void leDepotEnTvaNeProposePasLesPiecesDuCircuit() {
        List<String> codes = codesAuDepot(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE);

        assertThat(codes).contains("FACTURE", "DECLARATION_TVA");
        assertThat(codes).doesNotContain("CERTIFICAT_UTILISATION", "QUITTANCE_DGI");
    }

    @Test
    void leParametrageVoitToujoursToutesLesPieces() {
        // Filtrer l'écran d'administration ferait croire à un type disparu, et quelqu'un le
        // recréerait en double.
        List<DocumentRequirementDto> tous = requirementService.findByProcessus(ProcessusDocument.UTILISATION_CI_DOUANE);

        assertThat(tous).extracting(DocumentRequirementDto::getCodeDocument)
                .contains("BULLETIN_ANNOTE", "CHEQUE_CERTIFIE", "QUITTANCE_TRESOR");
        assertThat(tous).filteredOn(d -> "BULLETIN_ANNOTE".equals(d.getCodeDocument()))
                .allMatch(d -> Boolean.FALSE.equals(d.getDeposableParLeDemandeur()));
        assertThat(tous).filteredOn(d -> "DECLARATION_DOUANE".equals(d.getCodeDocument()))
                .allMatch(d -> Boolean.TRUE.equals(d.getDeposableParLeDemandeur()));
    }

    // ── le dépôt lui-même ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lEntrepriseNePeutPasDeposerUnBulletinAnnote() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.EN_CONTROLE_DGD);

        assertThatThrownBy(() -> documentService.upload(util.getId(), "BULLETIN_ANNOTE", null, pdf(), entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        assertThat(documentRepository.findByUtilisationCreditIdAndCodeDocumentAndActifTrue(util.getId(), "BULLETIN_ANNOTE"))
                .as("rien n'a été déposé").isEmpty();
    }

    @Test
    @Transactional
    void lEntrepriseNeDeposeNiQuittanceNiCertificatNiChequeParLeDepot() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.EN_CONTROLE_DGD);

        for (String code : List.of("QUITTANCE_TRESOR", "CERTIFICAT_UTILISATION", "CHEQUE_CERTIFIE")) {
            assertThatThrownBy(() -> documentService.upload(util.getId(), code, null, pdf(), entreprise))
                    .as(code)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        }
    }

    @Test
    @Transactional
    void lEntrepriseDeposeToujoursSesPropresPieces() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.DEMANDEE);

        assertThatCode(() -> documentService.upload(util.getId(), "DECLARATION_DOUANE", null, pdf(), entreprise))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void lePresidentDeposeToujoursLeCertificatDUtilisation() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);

        assertThatCode(() -> documentService.upload(util.getId(), "CERTIFICAT_UTILISATION", null, pdf(), president))
                .doesNotThrowAnyException();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private List<String> codesAuDepot(ProcessusDocument processus) {
        return requirementService.findByProcessus(processus, true).stream()
                .map(DocumentRequirementDto::getCodeDocument)
                .collect(Collectors.toList());
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "piece.pdf", "application/pdf", "%PDF-1.4 piece".getBytes());
    }

    private UtilisationDouaniere douaneAu(StatutUtilisation statut) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));
        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-DEP-" + System.nanoTime());
        util.setNumeroBulletin("BUL-DEP-" + System.nanoTime());
        return (UtilisationDouaniere) utilisationRepository.save(util);
    }
}
