package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaque soumission d'une demande de correction produit son reçu, et aucun n'est perdu.
 *
 * <p>Le reçu est la seule pièce que le système produise à chaque dépôt. La règle générale de
 * remplacement exige un rejet temporaire ouvert réclamant expressément le code remplacé — ce qui
 * n'arrive jamais pour un reçu, personne ne demandant à l'être. Sans exemption, la deuxième
 * soumission échouerait.
 *
 * <p>Le test qui compte est {@link #lexemptionNeVautQuePourLeRecu()} : une exemption trop large
 * rouvrirait le remplacement libre de toutes les pièces, ce que la règle existante interdit
 * justement.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecuDepotDemandeCorrectionIT {

    private static final String RECU = "RECU_DEPOT";

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser ac;

    @BeforeEach
    void setUp() {
        Utilisateur agent = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(agent.getId(), agent.getUsername(), agent.getRole());
    }

    // ── un reçu par dépôt ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void premierDepot_recuEnregistre() throws Exception {
        DemandeCorrection demande = uneDemande();

        DocumentDto recu = documentService.upload(demande.getId(), RECU, null, pdf("recu-1.pdf"), ac);

        assertThat(recu.getCodeDocument()).isEqualTo(RECU);
        assertThat(recu.getVersion()).isEqualTo(1);
    }

    @Test
    @Transactional
    void secondDepot_horsRejetTemporaire_accepte() throws Exception {
        // Le cas que la règle générale refusait : deux soumissions successives sans rejet ouvert.
        DemandeCorrection demande = uneDemande();
        documentService.upload(demande.getId(), RECU, null, pdf("recu-1.pdf"), ac);

        DocumentDto second = documentService.upload(demande.getId(), RECU, null, pdf("recu-2.pdf"), ac);

        assertThat(second.getVersion()).isEqualTo(2);
    }

    @Test
    @Transactional
    void lesRecusPrecedentsRestentRetrouvables() throws Exception {
        // « Retrouvable » est la décision actée : un reçu qu'on ne peut produire qu'une fois ne
        // prouve rien le jour où l'on en a besoin.
        DemandeCorrection demande = uneDemande();
        documentService.upload(demande.getId(), RECU, null, pdf("recu-1.pdf"), ac);
        documentService.upload(demande.getId(), RECU, null, pdf("recu-2.pdf"), ac);

        List<DocumentDto> recus = documentService.findByDemandeCorrectionId(demande.getId(), ac).stream()
                .filter(d -> RECU.equals(d.getCodeDocument()))
                .toList();

        assertThat(recus).hasSize(2);
        assertThat(recus).extracting(DocumentDto::getVersion).containsExactlyInAnyOrder(1, 2);
    }

    // ── l'exemption reste étroite ──────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lexemptionNeVautQuePourLeRecu() throws Exception {
        DemandeCorrection demande = uneDemande();
        documentService.upload(demande.getId(), "LETTRE_SAISINE", null, pdf("saisine-1.pdf"), ac);

        // Remplacer une pièce du demandeur reste soumis à la règle : rejet temporaire ouvert
        // réclamant ce code. Sinon l'exemption du reçu serait devenue un trou général.
        assertThatThrownBy(() ->
                documentService.upload(demande.getId(), "LETTRE_SAISINE", null, pdf("saisine-2.pdf"), ac))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("Remplacement interdit"));
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    /** Une demande qui n'est pas INCOMPLETE, pour que le contraste ne dépende pas d'un rejet ouvert. */
    private DemandeCorrection uneDemande() {
        return demandeRepository.findAll().stream()
                .filter(d -> d.getStatut() != StatutDemande.INCOMPLETE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande de correction amorcée"));
    }

    private MultipartFile pdf(String nom) {
        return new MockMultipartFile("file", nom, "application/pdf", "%PDF-1.4 recu".getBytes());
    }
}
