package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCorrectionService;
import mr.gov.finances.sgci.service.DecisionUtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un rejet temporaire ne réclame que des pièces paramétrées dans la GED pour le processus.
 *
 * <p>L'équipe a relevé que la liste proposée à l'agent ne venait pas de la GED : le front en tenait
 * une en dur, et le serveur acceptait n'importe quel code. Seule la mise en place contrôlait déjà.
 * La règle vaut désormais pour la correction et l'utilisation (et le transfert, même code).
 *
 * <p>Le rejet portant sur plusieurs pièces est aussi vérifié ici. Son échec en production venait du
 * schéma MySQL hérité (voir {@code DecisionDocumentsDemandesLegacyMigration}), que H2 ne reproduit
 * pas ; le test garantit au moins que le code enregistre bien toutes les pièces.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class RejetTempPiecesGedIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private DecisionCorrectionService correctionService;
    @Autowired private DecisionUtilisationCreditService utilisationService;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private DecisionCorrectionRepository decisionRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgd;

    @BeforeEach
    void setUp() {
        Utilisateur u = utilisateurRepository.findByUsername("dgd").orElseThrow();
        dgd = new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    // ── correction ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void correction_plusieursPiecesDeLaGedSontToutesEnregistrees() {
        DemandeCorrection d = demandeRecue();

        DecisionCorrectionDto rejet = correctionService.saveDecision(d.getId(), DecisionCorrectionType.REJET_TEMP,
                "Pièces illisibles", Set.of("LETTRE_SAISINE", " offre_fiscale ", "DAO_DQE"), dgd);

        assertThat(rejet.getDocumentsDemandes())
                .as("toutes les pièces, codes normalisés")
                .containsExactlyInAnyOrder("LETTRE_SAISINE", "OFFRE_FISCALE", "DAO_DQE");
    }

    @Test
    @Transactional
    void correction_unePieceHorsGedEstRefusee() {
        DemandeCorrection d = demandeRecue();

        // DECLARATION_TVA existe au catalogue, mais n'est pas paramétrée pour la correction.
        assertThatThrownBy(() -> correctionService.saveDecision(d.getId(), DecisionCorrectionType.REJET_TEMP,
                "Pièce manquante", Set.of("LETTRE_SAISINE", "DECLARATION_TVA"), dgd))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("DECLARATION_TVA");
        assertThat(decisionRepository.findByDemandeCorrectionId(d.getId())).as("aucun rejet posé").isEmpty();
    }

    // ── utilisation ────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void utilisation_plusieursPiecesDeLaGedSontAcceptees() {
        UtilisationDouaniere util = utilisationEnVerification();

        var rejet = utilisationService.saveDecision(util.getId(), DecisionCorrectionType.REJET_TEMP,
                "Pièces à compléter", Set.of("FACTURE", "DECLARATION_DOUANE"), dgd);

        assertThat(rejet.getDocumentsDemandes()).containsExactlyInAnyOrder("FACTURE", "DECLARATION_DOUANE");
    }

    @Test
    @Transactional
    void utilisation_unePieceDUnAutreProcessusEstRefusee() {
        UtilisationDouaniere util = utilisationEnVerification();

        // DECLARATION_TVA appartient à la TVA intérieure, pas à la douane.
        assertThatThrownBy(() -> utilisationService.saveDecision(util.getId(), DecisionCorrectionType.REJET_TEMP,
                "Pièce manquante", Set.of("DECLARATION_TVA"), dgd))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("DECLARATION_TVA");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    /** Une demande reçue, à crédit extérieur : la DGD vise en premier, donc peut rejeter. */
    private DemandeCorrection demandeRecue() {
        DemandeCorrection d = demandeRepository.findAll().stream().findFirst().orElseThrow();
        decisionRepository.deleteAll(decisionRepository.findByDemandeCorrectionId(d.getId()));
        d.getDecisions().clear();
        d.setStatut(StatutDemande.RECUE);
        d.setCreditExterieur(new BigDecimal("1000"));
        d.setCreditInterieur(new BigDecimal("500"));
        return demandeRepository.saveAndFlush(d);
    }

    private UtilisationDouaniere utilisationEnVerification() {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(StatutUtilisation.EN_VERIFICATION);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-REJ-" + System.nanoTime());
        util.setNumeroBulletin("BUL-REJ-" + System.nanoTime());
        return (UtilisationDouaniere) utilisationRepository.save(util);
    }
}
