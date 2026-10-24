package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.DataInitializer;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.SensDocument;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.service.DocumentRequirementValidator;
import mr.gov.finances.sgci.service.document.VisibiliteDocumentService;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.UpsertDocumentRequirementRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le paramétrage de la GED permet de désactiver ou de supprimer une pièce d'un processus.
 *
 * <p>Une suppression existait, mais elle effaçait la ligne, et cela ne tenait pas : le seed rejoue
 * à chaque démarrage et recréait toute pièce connue manquante. Effacer une pièce de sortie lui ôtait
 * aussi son sens : ses exemplaires déjà déposés devenaient un « code inconnu », lu comme une entrée,
 * donc visibles du déposant avant la fin du processus. Les tests
 * {@link #laSuppressionResisteAuRedemarrage()} et
 * {@link #unePieceDeSortieSupprimeeResteMasqueeAuDeposant()} portent ces deux défauts.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentRequirementActivationIT {

    private static final ProcessusDocument CORRECTION = ProcessusDocument.CORRECTION_OFFRE_FISCALE;

    @Autowired private DocumentRequirementService service;
    @Autowired private DocumentRequirementValidator validator;
    @Autowired private DocumentRequirementRepository repository;
    @Autowired private VisibiliteDocumentService visibilite;
    @Autowired private DataInitializer dataInitializer;

    // ── désactiver ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneDesactivationRetireLaPieceDuDepotMaisPasDuParametrage() {
        DocumentRequirementDto piece = nouvellePiece(true, SensDocument.ENTREE);

        service.changerActivation(piece.getId(), false);

        assertThat(codes(service.findByProcessus(CORRECTION, true)))
                .as("le formulaire de dépôt ne la propose plus").doesNotContain(piece.getCodeDocument());
        assertThat(service.findByProcessus(CORRECTION, false))
                .as("l'écran de paramétrage la garde, marquée inactive, pour pouvoir la réactiver")
                .filteredOn(d -> d.getCodeDocument().equals(piece.getCodeDocument()))
                .singleElement()
                .satisfies(d -> assertThat(d.getActif()).isFalse());
    }

    @Test
    @Transactional
    void unePieceObligatoireDesactiveeNEstPlusExigee() {
        DocumentRequirementDto piece = nouvellePiece(true, SensDocument.ENTREE);
        assertThatThrownBy(() -> validator.assertRequiredDocumentsPresent(CORRECTION, Set.of()))
                .as("tant qu'elle est active, elle est exigée")
                .hasMessageContaining(piece.getCodeDocument());

        service.changerActivation(piece.getId(), false);

        assertThatCode(() -> validator.assertRequiredDocumentsPresent(CORRECTION, Set.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void unePieceDesactiveeNeSeDeposeNiNeSeReclamePlus() {
        DocumentRequirementDto piece = nouvellePiece(false, SensDocument.ENTREE);
        assertThatCode(() -> validator.validateUpload(CORRECTION, piece.getCodeDocument(), pdf()))
                .as("active, elle se dépose").doesNotThrowAnyException();

        service.changerActivation(piece.getId(), false);

        assertThatThrownBy(() -> validator.validateUpload(CORRECTION, piece.getCodeDocument(), pdf()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("n'est plus demandé");
        assertThatThrownBy(() -> validator.assertDocumentsConfiguredForProcessus(
                CORRECTION, Set.of(piece.getCodeDocument())))
                .as("une demande de pièce ne peut plus la réclamer")
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void laReactivationRendLaPiece() {
        DocumentRequirementDto piece = nouvellePiece(false, SensDocument.ENTREE);
        service.changerActivation(piece.getId(), false);

        service.changerActivation(piece.getId(), true);

        assertThat(codes(service.findByProcessus(CORRECTION, true))).contains(piece.getCodeDocument());
    }

    // ── supprimer ──────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unePieceSupprimeeDisparaitDuParametrage() {
        DocumentRequirementDto piece = nouvellePiece(false, SensDocument.ENTREE);

        service.delete(piece.getId());

        assertThat(codes(service.findByProcessus(CORRECTION, false))).doesNotContain(piece.getCodeDocument());
        assertThat(codes(service.findByProcessus(CORRECTION, true))).doesNotContain(piece.getCodeDocument());
    }

    @Test
    @Transactional
    void laSuppressionResisteAuRedemarrage() {
        // Une pièce prévue par l'application, que le seed recrée si elle manque.
        DocumentRequirement prevue = repository.findByProcessusAndCodeDocument(CORRECTION, "TABLEAU_MODELE")
                .orElseThrow();
        service.delete(prevue.getId());

        // Le seed des pièces, rejoué exactement comme au démarrage du serveur.
        ReflectionTestUtils.invokeMethod(dataInitializer, "seedDocumentRequirements");

        assertThat(codes(service.findByProcessus(CORRECTION, false)))
                .as("la pièce supprimée ne doit pas revenir au démarrage suivant")
                .doesNotContain("TABLEAU_MODELE");
    }

    @Test
    @Transactional
    void unePieceDeSortieSupprimeeResteMasqueeAuDeposant() {
        // Effacée, la pièce aurait perdu son sens et serait devenue visible avant la fin du processus.
        DocumentRequirementDto sortie = nouvellePiece(false, SensDocument.SORTIE);

        service.delete(sortie.getId());

        assertThat(visibilite.visiblePour(CORRECTION, sortie.getCodeDocument(),
                StatutDemande.EN_EVALUATION, Role.ENTREPRISE))
                .as("une pièce de sortie supprimée reste masquée au déposant en cours d'instruction")
                .isFalse();
    }

    @Test
    @Transactional
    void recreerUnePieceSupprimeeLaRestaure() {
        DocumentRequirementDto piece = nouvellePiece(false, SensDocument.ENTREE);
        service.delete(piece.getId());

        DocumentRequirementDto restauree = service.create(requete(piece.getCodeDocument(), true, SensDocument.ENTREE));

        assertThat(restauree.getId()).as("même ligne, pas de doublon").isEqualTo(piece.getId());
        assertThat(restauree.getActif()).isTrue();
        assertThat(restauree.getObligatoire()).isTrue();
    }

    // ── les pièces du circuit ──────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesPiecesDuCircuitNeSeDesactiventNiNeSeSuppriment() {
        // Sans la lettre d'adoption, le Président ne pourrait plus adopter aucune demande.
        DocumentRequirement lettre = repository.findByProcessusAndCodeDocument(CORRECTION, "LETTRE_ADOPTION")
                .orElseThrow();

        assertThatThrownBy(() -> service.changerActivation(lettre.getId(), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("le circuit l'exige");
        assertThatThrownBy(() -> service.delete(lettre.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("le circuit l'exige");
        assertThat(repository.findById(lettre.getId()).orElseThrow().estUtilisable()).isTrue();
    }

    @Test
    @Transactional
    void leParametrageSignaleLesPiecesDuCircuit() {
        DocumentRequirementDto piece = nouvellePiece(false, SensDocument.ENTREE);

        List<DocumentRequirementDto> liste = service.findByProcessus(CORRECTION, false);

        assertThat(liste).filteredOn(d -> d.getCodeDocument().equals("LETTRE_ADOPTION"))
                .singleElement().satisfies(d -> assertThat(d.getPieceDuCircuit()).isTrue());
        assertThat(liste).filteredOn(d -> d.getCodeDocument().equals(piece.getCodeDocument()))
                .singleElement().satisfies(d -> assertThat(d.getPieceDuCircuit()).isFalse());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private DocumentRequirementDto nouvellePiece(boolean obligatoire, SensDocument sens) {
        return service.create(requete("PIECE_TEST_" + System.nanoTime(), obligatoire, sens));
    }

    private UpsertDocumentRequirementRequest requete(String code, boolean obligatoire, SensDocument sens) {
        return UpsertDocumentRequirementRequest.builder()
                .processus(CORRECTION)
                .codeDocument(code)
                .libelle("Pièce de test")
                .obligatoire(obligatoire)
                .sens(sens)
                .build();
    }

    private static List<String> codes(List<DocumentRequirementDto> liste) {
        return liste.stream().map(DocumentRequirementDto::getCodeDocument).toList();
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "piece.pdf", "application/pdf", new byte[]{1, 2, 3});
    }
}
