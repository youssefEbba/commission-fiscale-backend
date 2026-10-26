package mr.gov.finances.sgci;

import jakarta.persistence.EntityManager;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deux défauts de la demande de correction, trouvés en analysant les observations de l'équipe de test.
 *
 * <ul>
 *   <li><b>L'adoption est l'acte du Président.</b> La DGTCP pouvait la prononcer par la route de
 *       statut, et sans lettre d'adoption, puisque le contrôle de la lettre ne visait que le
 *       Président.</li>
 *   <li><b>La fiche de la demande livrait les pièces de sortie.</b> Le déposant y lisait les liens de
 *       la lettre d'adoption avant l'adoption, alors que la liste des documents les lui masque.</li>
 * </ul>
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdoptionEtPiecesCorrectionIT {

    @Autowired private DemandeCorrectionService service;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EntityManager entityManager;

    private AuthenticatedUser ac;
    private AuthenticatedUser dgtcp;
    private AuthenticatedUser president;
    private Long acId;

    @BeforeEach
    void setUp() {
        Utilisateur compteAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(compteAc.getId(), compteAc.getUsername(), compteAc.getRole());
        acId = compteAc.getAutoriteContractante().getId();
        dgtcp = compte("dgtcp");
        president = compte("president");
    }

    // ── l'adoption ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laDgtcpNePeutPasAdopter() {
        DemandeCorrection d = demandeEnValidation(false);

        assertThatThrownBy(() -> service.updateStatut(d.getId(), StatutDemande.ADOPTEE, dgtcp, null, true))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        assertThat(demandeRepository.findById(d.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutDemande.EN_VALIDATION);
    }

    @Test
    @Transactional
    void lePresidentNAdoptePasSansLettre() {
        DemandeCorrection d = demandeEnValidation(false);

        assertThatThrownBy(() -> service.updateStatut(d.getId(), StatutDemande.ADOPTEE, president, null, true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("LETTRE_ADOPTION");
    }

    @Test
    @Transactional
    void lePresidentAdopteAvecLaLettre() {
        DemandeCorrection d = demandeEnValidation(true);

        DemandeCorrectionDto dto = service.updateStatut(d.getId(), StatutDemande.ADOPTEE, president, null, true);

        assertThat(dto.getStatut()).isEqualTo(StatutDemande.ADOPTEE);
    }

    // ── les pièces de la fiche ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void avantLAdoption_laFicheNeLivrePasLaLettreAuDeposant() {
        DemandeCorrection d = demandeEnValidation(true);

        assertThat(codes(service.findById(d.getId(), ac)))
                .contains("OFFRE_FISCALE")
                .doesNotContain("LETTRE_ADOPTION");
        assertThat(service.findAll(ac)).filteredOn(x -> d.getId().equals(x.getId()))
                .singleElement()
                .satisfies(x -> assertThat(codes(x)).doesNotContain("LETTRE_ADOPTION"));
        assertThat(service.findByAutoriteContractante(acId, ac)).filteredOn(x -> d.getId().equals(x.getId()))
                .singleElement()
                .satisfies(x -> assertThat(codes(x)).doesNotContain("LETTRE_ADOPTION"));
    }

    @Test
    @Transactional
    void lesDirectionsVoientToutesLesPieces() {
        DemandeCorrection d = demandeEnValidation(true);

        assertThat(codes(service.findById(d.getId(), dgtcp))).contains("OFFRE_FISCALE", "LETTRE_ADOPTION");
    }

    @Test
    @Transactional
    void apresLAdoption_laLettreEstCommunicable() {
        DemandeCorrection d = demandeEnValidation(true);
        d.setStatut(StatutDemande.ADOPTEE);
        demandeRepository.saveAndFlush(d);

        assertThat(codes(service.findById(d.getId(), ac))).contains("LETTRE_ADOPTION");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private static List<String> codes(DemandeCorrectionDto dto) {
        return dto.getDocuments().stream().map(DocumentDto::getCodeDocument).toList();
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    /** Une demande de l'autorité du compte « ac », en validation présidentielle, avec son offre. */
    private DemandeCorrection demandeEnValidation(boolean avecLettre) {
        DemandeCorrection d = demandeRepository.findAll().stream()
                .filter(x -> x.getAutoriteContractante() != null && acId.equals(x.getAutoriteContractante().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande pour l'autorité du compte ac"));
        documentRepository.deleteAll(documentRepository.findByDemandeCorrectionId(d.getId()));
        d.setStatut(StatutDemande.EN_VALIDATION);
        d = demandeRepository.saveAndFlush(d);
        piece(d, "OFFRE_FISCALE");
        if (avecLettre) {
            piece(d, "LETTRE_ADOPTION");
        }
        // La fiche lit la collection de l'entité : sans ce vidage, elle ne verrait pas les pièces.
        entityManager.flush();
        entityManager.clear();
        return demandeRepository.findById(d.getId()).orElseThrow();
    }

    private void piece(DemandeCorrection d, String code) {
        documentRepository.saveAndFlush(Document.builder()
                .codeDocument(code)
                .nomFichier(code.toLowerCase() + ".pdf")
                .chemin("/api/local-files/" + code.toLowerCase() + "-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(1024L)
                .version(1)
                .actif(true)
                .demandeCorrection(d)
                .build());
    }
}
