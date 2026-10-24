package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCorrectionService;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.VisaCorrectionStatutDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La direction qui dépose l'offre corrigée saisit les crédits corrigés avec son visa.
 *
 * <p>C'est la DGD dès qu'il y a un crédit extérieur, sinon la DGI. L'écran lui propose les montants
 * déclarés par l'autorité contractante ; elle les garde ou les change. Les montants retenus
 * remplacent ceux de la demande, et la déclaration d'origine est conservée à part.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class MontantsCorrectionVisaIT {

    @Autowired private DecisionCorrectionService service;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private DecisionCorrectionRepository decisionRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgd;
    private AuthenticatedUser dgi;
    private AuthenticatedUser dgtcp;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        dgd = compte("dgd");
        dgi = compte("dgi");
        dgtcp = compte("dgtcp");
        admin = compte("admin");
    }

    // ── la DGD, quand il y a un crédit extérieur ───────────────────────────────────────────────

    @Test
    @Transactional
    void laDgdChangeLesMontantsAvecSonVisa() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        DecisionCorrectionDto visa = service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("900"), new BigDecimal("450"), dgd);

        DemandeCorrection apres = demandeRepository.findById(d.getId()).orElseThrow();
        assertThat(apres.getCreditExterieur()).as("montant retenu").isEqualByComparingTo("900");
        assertThat(apres.getCreditInterieur()).isEqualByComparingTo("450");
        assertThat(apres.getCreditExterieurDeclare()).as("déclaration de l'autorité conservée").isEqualByComparingTo("1000");
        assertThat(apres.getCreditInterieurDeclare()).isEqualByComparingTo("500");
        assertThat(visa.getCreditExterieurRetenu()).as("tracé sur le visa").isEqualByComparingTo("900");
        assertThat(visa.getCreditInterieurRetenu()).isEqualByComparingTo("450");
    }

    @Test
    @Transactional
    void laDgdPeutConserverLesMontantsDeclares() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("1000"), new BigDecimal("500"), dgd);

        DemandeCorrection apres = demandeRepository.findById(d.getId()).orElseThrow();
        assertThat(apres.getCreditExterieur()).isEqualByComparingTo("1000");
        assertThat(apres.getCreditExterieurDeclare()).isEqualByComparingTo("1000");
    }

    @Test
    @Transactional
    void unVisaDeLaDgdSansMontantsEstRefuse() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        assertThatThrownBy(() -> service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null, dgd))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Saisissez le crédit extérieur et le crédit intérieur");
        assertThat(decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                d.getId(), Role.DGD, DecisionCorrectionType.VISA)).as("aucun visa posé").isFalse();
    }

    @Test
    @Transactional
    void lesAutresDirectionsNeSaisissentPasDeMontants() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");
        service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("900"), new BigDecimal("450"), dgd);

        assertThatThrownBy(() -> service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("1"), new BigDecimal("1"), dgtcp))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("direction qui dépose l'offre corrigée");
        assertThat(demandeRepository.findById(d.getId()).orElseThrow().getCreditExterieur())
                .as("le montant retenu par la DGD ne bouge pas").isEqualByComparingTo("900");
    }

    // ── la DGI, quand il n'y a qu'un crédit intérieur ──────────────────────────────────────────

    @Test
    @Transactional
    void sansCreditExterieurCEstLaDgiQuiSaisit() {
        DemandeCorrection d = demande("0", "500", "CREDIT_INTERIEUR");

        service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                BigDecimal.ZERO, new BigDecimal("420"), dgi);

        DemandeCorrection apres = demandeRepository.findById(d.getId()).orElseThrow();
        assertThat(apres.getCreditInterieur()).isEqualByComparingTo("420");
        assertThat(apres.getCreditInterieurDeclare()).isEqualByComparingTo("500");
    }

    // ── la cohérence des montants ──────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesMontantsNegatifsOuTousNulsSontRefuses() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        assertThatThrownBy(() -> service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("-1"), new BigDecimal("500"), dgd))
                .hasMessageContaining("négatifs");
        assertThatThrownBy(() -> service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, dgd))
                .hasMessageContaining("Au moins un des deux crédits");
    }

    @Test
    @Transactional
    void laDeclarationDOrigineNEstSaisieQuUneFois() {
        // Après une réouverture sur réclamation, une seconde correction ne doit pas faire passer la
        // première pour la déclaration de l'autorité.
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");
        d.setCreditExterieurDeclare(new BigDecimal("1200"));
        d.setCreditInterieurDeclare(new BigDecimal("600"));
        demandeRepository.saveAndFlush(d);

        service.saveDecision(d.getId(), DecisionCorrectionType.VISA, null, null,
                new BigDecimal("800"), new BigDecimal("400"), dgd);

        DemandeCorrection apres = demandeRepository.findById(d.getId()).orElseThrow();
        assertThat(apres.getCreditExterieurDeclare()).isEqualByComparingTo("1200");
        assertThat(apres.getCreditExterieur()).isEqualByComparingTo("800");
    }

    // ── l'écran des visas et l'administrateur ──────────────────────────────────────────────────

    @Test
    @Transactional
    void lEcranDesVisasSaitQuiDoitSaisirLesMontants() {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        List<VisaCorrectionStatutDto> visas = service.visaStatuts(d.getId());

        assertThat(visas).filteredOn(v -> v.isSaisieMontantsExigee())
                .extracting(VisaCorrectionStatutDto::getRole)
                .containsExactly(Role.DGD);
    }

    @Test
    @Transactional
    void lAdministrateurQuiVisePourLaDgdSaisitAussiLesMontants() throws Exception {
        DemandeCorrection d = demande("1000", "500", "OFFRE_FISCALE_CORRIGEE");

        assertThatThrownBy(() -> service.adminVisaPourRole(d.getId(), Role.DGD, "Agent DGD absent", null, admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        service.adminVisaPourRole(d.getId(), Role.DGD, "Agent DGD absent", null,
                new BigDecimal("950"), new BigDecimal("480"), admin);

        DemandeCorrection apres = demandeRepository.findById(d.getId()).orElseThrow();
        assertThat(apres.getCreditExterieur()).isEqualByComparingTo("950");
        assertThat(apres.getCreditExterieurDeclare()).isEqualByComparingTo("1000");
        DecisionCorrection visa = decisionRepository.findByDemandeCorrectionId(d.getId()).stream()
                .filter(x -> x.getRole() == Role.DGD).findFirst().orElseThrow();
        assertThat(visa.getVisaParAdmin()).isTrue();
        assertThat(visa.getCreditExterieurRetenu()).isEqualByComparingTo("950");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    /** Une demande reçue, sans visa, avec la pièce de correction déjà déposée. */
    private DemandeCorrection demande(String exterieur, String interieur, String pieceDeCorrection) {
        DemandeCorrection d = demandeRepository.findAll().stream().findFirst().orElseThrow();
        decisionRepository.deleteAll(decisionRepository.findByDemandeCorrectionId(d.getId()));
        d.getDecisions().clear();
        d.setStatut(StatutDemande.RECUE);
        d.setCreditExterieur(new BigDecimal(exterieur));
        d.setCreditInterieur(new BigDecimal(interieur));
        d.setCreditExterieurDeclare(null);
        d.setCreditInterieurDeclare(null);
        d = demandeRepository.saveAndFlush(d);
        documentRepository.saveAndFlush(Document.builder()
                .codeDocument(pieceDeCorrection)
                .nomFichier(pieceDeCorrection.toLowerCase() + ".pdf")
                .chemin("/api/local-files/" + pieceDeCorrection.toLowerCase() + "-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(1024L)
                .version(1)
                .actif(true)
                .demandeCorrection(d)
                .build());
        return d;
    }
}
