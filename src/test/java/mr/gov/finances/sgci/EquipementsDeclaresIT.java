package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Équipements et véhicules sous régime déclarés sur une demande de correction.
 *
 * <p>La règle est conditionnelle : cocher la case impose de joindre la liste. Elle ne peut pas être
 * contrôlée à la création — une demande naît en JSON et reçoit ses pièces ensuite — donc elle est
 * posée aux deux portes où les documents existent : la soumission d'un brouillon et l'examen de
 * recevabilité.
 *
 * <p>{@link #sansDeclaration_aucunePieceReclamee()} est le garde-fou le plus utile : une obligation
 * conditionnelle mal écrite se trahit en devenant inconditionnelle, et bloquerait alors toutes les
 * demandes sans équipement.
 *
 * <p>L'ouverture du crédit n'est volontairement pas testée ici : aucun contrôle ne lui a été ajouté.
 * L'alerte est un avertissement, décidé comme tel — le drapeau remonte jusqu'au certificat
 * ({@link #leDrapeauRemonteJusquAuCertificat()}), et c'est l'écran qui avertit.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class EquipementsDeclaresIT {

    private static final String LISTE = "LISTE_EQUIPEMENTS";

    @Autowired
    private DemandeCorrectionService service;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgd;
    private AuthenticatedUser ac;

    @BeforeEach
    void setUp() {
        Utilisateur agentDgd = utilisateurRepository.findByUsername("dgd").orElseThrow();
        dgd = new AuthenticatedUser(agentDgd.getId(), agentDgd.getUsername(), agentDgd.getRole());
        Utilisateur agentAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(agentAc.getId(), agentAc.getUsername(), agentAc.getRole());
    }

    // ── la liste n'est plus réclamée ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void declarationSansListe_recevabiliteAcceptee() {
        // Décision de la Commission : la liste reste une pièce du référentiel, mais elle n'est
        // jamais exigée. La règle qui bloquait la recevabilité a donc été retirée, et ce test est
        // l'inverse exact de celui qu'il remplace — il existe pour que personne ne la remette sans
        // le vouloir, en croyant réparer un oubli.
        DemandeCorrection demande = demandeAu(StatutDemande.RECUE, true);

        assertThatCode(() -> service.updateStatut(
                demande.getId(), StatutDemande.RECEVABLE, dgd, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void declarationAvecListe_recevabiliteAcceptee() throws Exception {
        DemandeCorrection demande = demandeAu(StatutDemande.RECUE, true);
        deposerListe(demande.getId());

        DemandeCorrectionDto dto = service.updateStatut(
                demande.getId(), StatutDemande.RECEVABLE, dgd, null, null);

        assertThat(dto.getStatut()).isEqualTo(StatutDemande.RECEVABLE);
    }

    @Test
    @Transactional
    void sansDeclaration_aucunePieceReclamee() {
        // Y compris sur un dossier à crédit extérieur : la pièce ne tient qu'à la case cochée.
        DemandeCorrection demande = demandeAu(StatutDemande.RECUE, false);

        assertThatCode(() -> service.updateStatut(
                demande.getId(), StatutDemande.RECEVABLE, dgd, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void declarationNonRenseignee_vautNon() {
        // Les dossiers antérieurs à cette règle ont la colonne à null : la question ne leur a pas
        // été posée, et elle ne doit pas se retourner contre eux.
        DemandeCorrection demande = demandeAu(StatutDemande.RECUE, null);

        assertThatCode(() -> service.updateStatut(
                demande.getId(), StatutDemande.RECEVABLE, dgd, null, null))
                .doesNotThrowAnyException();
    }

    // ── ni à la soumission d'un brouillon ──────────────────────────────────────────────────────

    @Test
    @Transactional
    void brouillonDeclarantSansListe_soumissionAcceptee() {
        DemandeCorrection demande = demandeAu(StatutDemande.BROUILLON, true);

        assertThatCode(() -> service.soumettreBrouillon(demande.getId(), ac))
                .doesNotThrowAnyException();
    }

    // ── le drapeau voyage ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leDrapeauRemonteDansLaDemande() {
        DemandeCorrection demande = demandeAu(StatutDemande.RECUE, true);

        DemandeCorrectionDto dto = service.findById(demande.getId(), dgd);

        assertThat(dto.getEquipementsPresents()).isTrue();
    }

    @Test
    @Transactional
    void leDrapeauRemonteJusquAuCertificat() {
        // C'est ce report qui permet d'avertir au moment d'ouvrir le crédit, sans rien dupliquer :
        // le certificat lit la déclaration de sa demande d'origine.
        CertificatCredit certificat = certificatRepository.findAll().stream()
                .filter(c -> c.getDemandeCorrection() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun certificat rattaché à une demande"));
        DemandeCorrection demande = certificat.getDemandeCorrection();
        demande.setEquipementsPresents(Boolean.TRUE);
        demandeRepository.save(demande);

        CertificatCreditDto dto = certificatService(certificat.getId());

        assertThat(dto.getEquipementsPresents()).isTrue();
    }

    @Test
    @Transactional
    void leDrapeauRemonteDansLaFileDeCloture() {
        // C'est le moment qui compte vraiment : fermer un crédit dont des équipements restent sous
        // régime est précisément ce que la Commission veut voir avant de signer.
        CertificatCredit certificat = certificatRepository.findAll().stream()
                .filter(c -> c.getDemandeCorrection() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun certificat rattaché à une demande"));
        certificat.setStatut(mr.gov.finances.sgci.domain.enums.StatutCertificat.OUVERT);
        certificatRepository.save(certificat);
        DemandeCorrection demande = certificat.getDemandeCorrection();
        demande.setEquipementsPresents(Boolean.TRUE);
        demandeRepository.save(demande);

        Utilisateur agentDgtcp = utilisateurRepository.findByUsername("dgtcp").orElseThrow();
        var file = clotureService.findClotureQueue(
                new AuthenticatedUser(agentDgtcp.getId(), agentDgtcp.getUsername(), agentDgtcp.getRole()));

        assertThat(file)
                .filteredOn(item -> certificat.getId().equals(item.getCertificatCreditId()))
                .isNotEmpty()
                .allMatch(item -> Boolean.TRUE.equals(item.getEquipementsPresents()))
                // Le drapeau sans le lien annoncerait des équipements sans permettre de les
                // regarder : les pièces sont rattachées à la demande, pas au certificat.
                .allMatch(item -> demande.getId().equals(item.getDemandeCorrectionId()));
    }

    // ── la pièce est bien classée ──────────────────────────────────────────────────────────────

    @Test
    void laListeEstUnePieceDuDemandeur() {
        // Si elle basculait du côté des pièces de la Commission, le formulaire de dépôt cesserait
        // de la proposer — et la règle ci-dessus deviendrait impossible à satisfaire.
        assertThat(DocumentDepotPolicy.CODES_DEPOSES_PAR_LE_DEMANDEUR).contains(LISTE);
        assertThat(DocumentDepotPolicy.deposableParLeDemandeur(ProcessusDocument.CORRECTION_OFFRE_FISCALE, LISTE)).isTrue();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    @Autowired
    private mr.gov.finances.sgci.service.CertificatCreditService certificatCreditService;

    @Autowired
    private mr.gov.finances.sgci.service.ClotureCreditService clotureService;

    private CertificatCreditDto certificatService(Long id) {
        Utilisateur president = utilisateurRepository.findByUsername("president").orElseThrow();
        return certificatCreditService.findById(id,
                new AuthenticatedUser(president.getId(), president.getUsername(), president.getRole()));
    }

    private DemandeCorrection demandeAu(StatutDemande statut, Boolean equipements) {
        DemandeCorrection demande = demandeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande de correction amorcée"));
        demande.setStatut(statut);
        demande.setEquipementsPresents(equipements);
        // Un crédit extérieur positif : c'est le cas que vise la règle, et sans lui le contrôle des
        // enveloppes refuserait la soumission avant que celui des équipements ne s'exprime.
        demande.setCreditExterieur(java.math.BigDecimal.valueOf(1_000_000));
        return demandeRepository.save(demande);
    }

    private void deposerListe(Long demandeId) throws Exception {
        documentService.upload(demandeId, LISTE, null,
                new MockMultipartFile("file", "equipements.pdf", "application/pdf",
                        "%PDF-1.4 liste".getBytes()),
                ac);
    }
}
