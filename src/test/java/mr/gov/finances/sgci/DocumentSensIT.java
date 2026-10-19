package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.service.document.VisibiliteDocumentService;
import mr.gov.finances.sgci.web.dto.DocumentDto;
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
 * Les pièces produites par la Commission ne parviennent au déposant qu'à la fin du processus.
 *
 * <p>{@link #laLettreDAdoptionResteInvisibleAvantLAdoption()} est le test central, et il échoue sur
 * le code d'avant ce lot : la liste des pièces d'une demande de correction n'appliquait ni périmètre
 * ni filtre de statut, et l'entreprise recevait l'offre corrigée et la lettre d'adoption dès leur
 * dépôt — donc en pleine instruction.
 *
 * <p>{@link #elleApparaitDesLAdoption()} existe pour la raison inverse, et compte autant : sans lui,
 * on pourrait tout masquer et croire le lot réussi. Une pièce qui n'arrive jamais est un défaut
 * aussi grave qu'une pièce qui arrive trop tôt.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentSensIT {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VisibiliteDocumentService visibilite;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private DemandeCorrection demande;
    private AuthenticatedUser deposant;

    @BeforeEach
    void setUp() {
        demande = demandeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        // Le déposant de ce dossier précis : le périmètre doit le laisser passer.
        Utilisateur compte = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ENTREPRISE && u.getEntreprise() != null
                        && demande.getEntreprise() != null
                        && u.getEntreprise().getId().equals(demande.getEntreprise().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun compte entreprise sur ce dossier"));
        deposant = new AuthenticatedUser(compte.getId(), compte.getUsername(), Role.ENTREPRISE);
    }

    // ── le cœur du lot ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laLettreDAdoptionResteInvisibleAvantLAdoption() {
        deposerPiece("LETTRE_ADOPTION");
        deposerPiece("OFFRE_FISCALE");
        basculer(StatutDemande.EN_EVALUATION);

        List<DocumentDto> vues = documentService.findByDemandeCorrectionId(demande.getId(), deposant);

        assertThat(vues).extracting(DocumentDto::getCodeDocument)
                .as("une pièce produite par la Commission, en pleine instruction")
                .doesNotContain("LETTRE_ADOPTION")
                // …et la pièce que le déposant a lui-même fournie reste visible.
                .contains("OFFRE_FISCALE");
    }

    @Test
    @Transactional
    void elleApparaitDesLAdoption() {
        // Le pendant du test précédent : tout masquer n'est pas réussir.
        deposerPiece("LETTRE_ADOPTION");
        basculer(StatutDemande.ADOPTEE);

        assertThat(documentService.findByDemandeCorrectionId(demande.getId(), deposant))
                .extracting(DocumentDto::getCodeDocument)
                .contains("LETTRE_ADOPTION");
    }

    @Test
    @Transactional
    void laNotificationNeRefermePasLesPieces() {
        // Un statut terminal postérieur à l'adoption ne doit pas reprendre ce qu'elle a ouvert.
        deposerPiece("LETTRE_ADOPTION");
        basculer(StatutDemande.NOTIFIEE);

        assertThat(documentService.findByDemandeCorrectionId(demande.getId(), deposant))
                .extracting(DocumentDto::getCodeDocument)
                .contains("LETTRE_ADOPTION");
    }

    @Test
    @Transactional
    void unRejetNOuvreRien() {
        deposerPiece("LETTRE_ADOPTION");
        basculer(StatutDemande.REJETEE);

        assertThat(documentService.findByDemandeCorrectionId(demande.getId(), deposant))
                .extracting(DocumentDto::getCodeDocument)
                .doesNotContain("LETTRE_ADOPTION");
    }

    // ── les directions ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesDirectionsVoientToutATousLesStatuts() {
        // Elles instruisent le dossier : leur masquer une pièce les empêcherait de travailler.
        deposerPiece("LETTRE_ADOPTION");
        basculer(StatutDemande.EN_EVALUATION);

        for (Role role : List.of(Role.DGD, Role.DGI, Role.DGTCP, Role.DGB,
                Role.PRESIDENT, Role.ADMIN_SI)) {
            Utilisateur u = utilisateurRepository.findAll().stream()
                    .filter(x -> x.getRole() == role).findFirst().orElse(null);
            if (u == null) {
                continue;
            }
            AuthenticatedUser acteur = new AuthenticatedUser(u.getId(), u.getUsername(), role);
            assertThat(documentService.findByDemandeCorrectionId(demande.getId(), acteur))
                    .as("la direction %s doit voir la pièce en instruction", role)
                    .extracting(DocumentDto::getCodeDocument)
                    .contains("LETTRE_ADOPTION");
        }
    }

    // ── le périmètre ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneEntrepriseNeLitPasLesPiecesDUnAutreDossier() {
        // Sans ce contrôle, restreindre la visibilité n'aurait servi à rien : il aurait suffi
        // d'ouvrir le dossier d'un autre.
        Utilisateur autre = utilisateurRepository.findByUsername("test").orElseThrow();
        AuthenticatedUser etranger =
                new AuthenticatedUser(autre.getId(), autre.getUsername(), Role.ENTREPRISE);

        assertThatThrownBy(() -> documentService.findByDemandeCorrectionId(demande.getId(), etranger))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
    }

    // ── le défaut penche du bon côté ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unCodeInconnuResteVisible() {
        // Une pièce absente du référentiel n'a pas de sens paramétré. Elle se comporte comme une
        // entrée — visible. C'est le défaut le moins nuisible, et un test de couverture du
        // référentiel garantit par ailleurs qu'aucune pièce connue n'est oubliée.
        assertThat(visibilite.visiblePour(ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                "CODE_QUI_NEXISTE_PAS", StatutDemande.EN_EVALUATION, Role.ENTREPRISE))
                .isTrue();
    }

    @Test
    @Transactional
    void leRecuDeDepotResteVisibleImmediatement() {
        // La preuve de dépôt est due au déposant tout de suite : la classer en sortie la lui
        // masquerait jusqu'à l'adoption, ce qui serait un recul.
        assertThat(visibilite.visiblePour(ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                "RECU_DEPOT", StatutDemande.EN_EVALUATION, Role.ENTREPRISE))
                .isTrue();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private void basculer(StatutDemande statut) {
        demande.setStatut(statut);
        demande = demandeRepository.saveAndFlush(demande);
    }

    private void deposerPiece(String code) {
        documentRepository.saveAndFlush(Document.builder()
                .codeDocument(code)
                .nomFichier(code.toLowerCase() + ".pdf")
                .chemin("/api/local-files/" + code.toLowerCase() + "-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(1024L)
                .version(1)
                .actif(true)
                .demandeCorrection(demande)
                .build());
    }
}
