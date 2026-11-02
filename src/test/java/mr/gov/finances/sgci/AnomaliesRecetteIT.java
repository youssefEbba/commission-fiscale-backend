package mr.gov.finances.sgci;

import com.fasterxml.jackson.databind.ObjectMapper;
import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.MotifCloture;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.ReferentielTypeDocumentRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.service.AuditLogQueryService;
import mr.gov.finances.sgci.service.AuditService;
import mr.gov.finances.sgci.service.CertificatVerificationService;
import mr.gov.finances.sgci.service.ClotureCreditService;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.service.DocumentCertificatCreditService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.AuditLogDto;
import mr.gov.finances.sgci.web.dto.CertificatClotureQueueItemDto;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateClotureCreditRequest;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les anomalies relevées pendant les captures du 02/11/2026 et corrigées côté serveur
 * ({@code Anomalies_relevees.docx}). Un test par fiche, nommé d'après son numéro.
 *
 * <p>Niveau service, comme les autres tests de ce dépôt : les règles se lisent sans démarrer
 * Tomcat.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnomaliesRecetteIT {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DocumentCertificatCreditService documentCertificatService;
    @Autowired private ClotureCreditService clotureService;
    @Autowired private MainCouranteService mainCouranteService;
    @Autowired private PermissionService permissionService;
    @Autowired private ConventionService conventionService;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private EffectiveIdentityService effectiveIdentityService;
    @Autowired private AuditService auditService;
    @Autowired private AuditLogQueryService auditLogQueryService;
    @Autowired private CertificatVerificationService verificationService;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private TransfertCreditRepository transfertRepository;
    @Autowired private AvenantRepository avenantRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ReferentielTypeDocumentRepository typeDocumentRepository;

    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire recette " + System.nanoTime())
                .nif("NIF-REC-" + System.nanoTime())
                .build());
    }

    // ── A41 : le TTC n'écrase plus le HT ─────────────────────────────────────────────────────

    @Test
    void A41_leTtcNEcrasePlusLeHt() throws Exception {
        CreateMarcheRequest r = objectMapper.readValue(
                "{\"montantContratHt\": 45000000, \"montantContratTtc\": 52200000}", CreateMarcheRequest.class);

        assertThat(r.getMontantContratHt()).isEqualByComparingTo("45000000");
        assertThat(r.getMontantContratTtc()).isEqualByComparingTo("52200000");
    }

    // ── A43 : le certificat de crédit d'impôt est réservé au Président ──────────────────────

    @Test
    void A43_lAutoriteContractanteNeDeposePasLeCertificatCreditImpots() {
        assertThat(DocumentDepotPolicy.deposableParLeDemandeur(ProcessusDocument.MISE_EN_PLACE_CI,
                "CERTIFICAT_CREDIT_IMPOTS")).isFalse();

        CertificatCredit c = certificatOuvert(new BigDecimal("800"), new BigDecimal("500"));
        MockMultipartFile pdf = new MockMultipartFile("file", "certificat.pdf", "application/pdf",
                "%PDF-1.4 certificat".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documentCertificatService.upload(c.getId(), "CERTIFICAT_CREDIT_IMPOTS", null, pdf,
                null, auth("ac", Role.AUTORITE_CONTRACTANTE)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(403));
    }

    // ── A24 : tout crédit ouvert sans opération en cours peut être proposé à la clôture ─────

    @Test
    @Transactional
    void A24_unCreditAvecSoldePeutEtreProposeMaisPasPourSoldeZero() {
        CertificatCredit c = certificatOuvert(new BigDecimal("800"), new BigDecimal("500"));
        AuthenticatedUser dgtcp = auth("dgtcp", Role.DGTCP);

        CertificatClotureQueueItemDto item = clotureService.findClotureQueue(dgtcp).stream()
                .filter(i -> i.getCertificatCreditId().equals(c.getId()))
                .findFirst().orElseThrow();

        assertThat(item.isEligiblePourCloture()).isTrue();
        assertThat(item.isSoldesEpuises()).isFalse();
        assertThat(item.isDetecteAutomatiquement()).isFalse();
        assertThat(item.getReference()).isEqualTo(c.getReference());
        assertThatThrownBy(() -> clotureService.proposer(
                new CreateClotureCreditRequest(c.getId(), MotifCloture.SOLDE_ZERO, TypeOperationCloture.CLOTURE), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(400));
    }

    // ── A17, A22, A28 : la main courante ne propose que ce qui attend vraiment le rôle ──────

    @Test
    @Transactional
    void A17_unTransfertTermineNeResteDansAucuneMainCourante() {
        CertificatCredit c = certificatOuvert(new BigDecimal("800"), new BigDecimal("500"));
        // Un seul transfert par crédit : le transfert terminé porte sur un autre crédit.
        TransfertCredit termine = transfertRepository.saveAndFlush(TransfertCredit.builder()
                .certificatCredit(certificatOuvert(new BigDecimal("800"), new BigDecimal("500")))
                .montant(BigDecimal.TEN).dateDemande(Instant.now()).statut(StatutTransfert.TRANSFERE)
                .build());
        TransfertCredit enCours = transfertRepository.saveAndFlush(TransfertCredit.builder()
                .certificatCredit(c).montant(BigDecimal.TEN).dateDemande(Instant.now()).statut(StatutTransfert.DEMANDE)
                .build());

        List<LigneMainCouranteDto> dgd = lignes("dgd", Role.DGD, "TRANSFERT");

        assertThat(dgd).extracting(LigneMainCouranteDto::getId).doesNotContain(termine.getId()).contains(enCours.getId());
        // Désigné par la référence du crédit, et non par un numéro interne nu.
        assertThat(dgd).filteredOn(l -> l.getId().equals(enCours.getId()))
                .extracting(LigneMainCouranteDto::getReference)
                .allMatch(ref -> ref.startsWith(c.getReference()));
    }

    @Test
    void A28_laDgbNAAucuneTacheDeMiseEnPlace() {
        assertThat(lignes("dgb", Role.DGB, "MISE_EN_PLACE")).isEmpty();
    }

    @Test
    @Transactional
    void A22_unAvenantDeposeAttendLaDgtcp() {
        CertificatCredit c = certificatOuvert(new BigDecimal("800"), new BigDecimal("500"));
        Avenant avenant = avenantRepository.saveAndFlush(Avenant.builder()
                .numero("AV-REC-" + System.nanoTime())
                .statut(StatutAvenant.DEPOSE)
                .certificatCredit(c)
                .dateDepot(Instant.now())
                .build());

        assertThat(lignes("dgtcp", Role.DGTCP, "AVENANT"))
                .extracting(LigneMainCouranteDto::getId).contains(avenant.getId());
    }

    // ── A45 et A30 : les droits ─────────────────────────────────────────────────────────────

    @Test
    void A45_lePresidentNaPasLesSuppleancesDeLAdministrateur() {
        assertThat(permissionService.findPermissionCodesByRole(Role.PRESIDENT))
                .noneMatch(code -> code.endsWith("admin_override"));
        assertThat(permissionService.findPermissionCodesByRole(Role.ADMIN_SI))
                .contains("certificat.visa.admin_override");
    }

    @Test
    void A30_uneEntrepriseNeLitQueLesConventionsDeSesDossiers() {
        Set<String> codes = permissionService.findPermissionCodesByRole(Role.ENTREPRISE);
        assertThat(codes).doesNotContain("convention.view.all", "projet.view.all").contains("convention.view", "projet.view");

        AuthenticatedUser entreprise = auth("entreprise", Role.ENTREPRISE);
        Utilisateur u = utilisateurRepository.findById(entreprise.getUserId()).orElseThrow();
        Long entId = effectiveIdentityService.resolveEntrepriseId(entreprise, u);
        List<Long> siennes = entId == null ? List.of() : conventionRepository.findIdsPourEntreprise(entId);

        List<ConventionDto> visibles = conventionService.findByStatut(null, entreprise);

        assertThat(visibles).extracting(ConventionDto::getId).allMatch(siennes::contains);
    }

    // ── A29 : le journal d'audit montre la référence et le motif ────────────────────────────

    @Test
    void A29_leJournalMontreLaReferenceEtLeMotif() {
        DemandeCorrection demande = demandeRepository.findAll().stream()
                .filter(d -> d.getReference() != null).findFirst().orElseThrow();
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(demande.getId()),
                Map.of("motif", "substitution de test"));

        AuditLogDto entree = auditLogQueryService.findWithFilters(null, "DemandeCorrection", AuditAction.UPDATE,
                        null, null, PageRequest.of(0, 50))
                .getContent().stream()
                .filter(a -> String.valueOf(demande.getId()).equals(a.getEntityId()))
                .findFirst().orElseThrow();

        assertThat(entree.getEntityReference()).isEqualTo(demande.getReference());
        assertThat(entree.getMotif()).isEqualTo("substitution de test");
        assertThat(entree.getTimestamp()).isNotNull();
    }

    // ── A14 : la vérification publique accepte la référence CR-… ────────────────────────────

    @Test
    void A14_laVerificationAccepteLaReferenceDuCredit() {
        CertificatCredit c = certificatRepository.findAll().stream()
                .filter(x -> x.getReference() != null && !x.getReference().equals(x.getNumero()))
                .findFirst().orElseThrow();

        assertThat(verificationService.verifyByNumero(c.getReference()).isTrouve()).isTrue();
    }

    // ── A09 : les libellés des pièces portent leurs accents ─────────────────────────────────

    @Test
    void A09_lesLibellesDesPiecesSontAccentues() {
        assertThat(typeDocumentRepository.findById("PIECE_IDENTITE").orElseThrow().getLibelle())
                .isEqualTo("Pièce d'identité");
        assertThat(typeDocumentRepository.findById("DECLARATION_DOUANE").orElseThrow().getLibelle())
                .isEqualTo("Déclaration en douane");
    }

    // ── utilitaires ─────────────────────────────────────────────────────────────────────────

    private CertificatCredit certificatOuvert(BigDecimal soldeCordon, BigDecimal soldeTva) {
        long n = System.nanoTime();
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-REC-" + n)
                .reference("CR-REC-" + n)
                .statut(StatutCertificat.OUVERT)
                .entreprise(titulaire)
                .dateEmission(Instant.now())
                .dateMiseEnPlace(Instant.now())
                .montantCordon(soldeCordon)
                .montantTVAInterieure(soldeTva)
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTva)
                .tvaImportationDouane(BigDecimal.ZERO)
                .build());
    }

    private List<LigneMainCouranteDto> lignes(String username, Role role, String processus) {
        return mainCouranteService.pourMoi(auth(username, role)).getLignes().stream()
                .filter(l -> processus.equals(l.getProcessus()))
                .toList();
    }

    private AuthenticatedUser auth(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), role);
    }
}
