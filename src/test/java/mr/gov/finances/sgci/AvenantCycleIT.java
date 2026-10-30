package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AvenantService;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.web.dto.ApplicationAvenantRequest;
import mr.gov.finances.sgci.web.dto.AvenantDto;
import mr.gov.finances.sgci.web.dto.CreateAvenantRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le cycle de l'avenant : Déposé → En cours de traitement → Adopté → Appliqué, ou Rejeté.
 *
 * <p>Déposé par l'entreprise ou l'autorité contractante, examiné par la DGTCP, validé par le
 * Président. Avec un impact financier, une demande de correction suit le circuit habituel, et
 * l'avenant n'est adopté qu'avec elle. La DGTCP l'applique ensuite : le consommé est conservé, le
 * restant remplacé.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class AvenantCycleIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private AvenantService service;
    @Autowired private DemandeCorrectionService demandeService;
    @Autowired private mr.gov.finances.sgci.service.CertificatCreditService certificatService;
    @Autowired private AvenantRepository avenantRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser autreEntreprise;
    private AuthenticatedUser ac;
    private AuthenticatedUser dgtcp;
    private AuthenticatedUser president;
    private AuthenticatedUser dgd;
    private CertificatCredit certificat;

    @BeforeEach
    void setUp() {
        entreprise = compte("entreprise");
        autreEntreprise = compte("test");
        ac = compte("ac");
        dgtcp = compte("dgtcp");
        president = compte("president");
        dgd = compte("dgd");
    }

    // ── le dépôt ───────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lEntrepriseEtLAutoriteDeposentUnAvenant() {
        preparerCertificat();

        AvenantDto parEntreprise = service.deposer(depot(TypeAvenant.CONTRAT), entreprise);
        AvenantDto parAutorite = service.deposer(depot(TypeAvenant.JURIDIQUE), ac);

        assertThat(parEntreprise.getStatut()).isEqualTo(StatutAvenant.DEPOSE);
        assertThat(parEntreprise.getNumero()).matches("^AV-\\d{4}-\\d{2}-\\d{4}$");
        assertThat(parAutorite.getStatut()).isEqualTo(StatutAvenant.DEPOSE);
    }

    @Test
    @Transactional
    void onNeDeposePasSurLeCreditDUnAutre() {
        preparerCertificat();

        assertThatThrownBy(() -> service.deposer(depot(TypeAvenant.CONTRAT), autreEntreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    @Test
    @Transactional
    void unCreditNonOuvertNeRecoitPasDAvenant() {
        preparerCertificat();
        certificat.setStatut(StatutCertificat.CLOTURE);
        certificatRepository.saveAndFlush(certificat);

        assertThatThrownBy(() -> service.deposer(depot(TypeAvenant.CONTRAT), entreprise))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void unAvenantFinancierSansCreditsProposesEstRefuse() {
        preparerCertificat();

        assertThatThrownBy(() -> service.deposer(depot(TypeAvenant.FINANCIER), entreprise))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("crédits proposés");
    }

    // ── examen et validation ───────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laDgtcpPrendEnChargeEtLePresidentAdopteUnAvenantNonFinancier() {
        preparerCertificat();
        AvenantDto a = service.deposer(depot(TypeAvenant.JURIDIQUE), entreprise);

        assertThat(service.prendreEnCharge(a.getId(), null, dgtcp).getStatut()).isEqualTo(StatutAvenant.EN_COURS_TRAITEMENT);
        AvenantDto valide = service.valider(a.getId(), null, president);

        assertThat(valide.getStatut()).isEqualTo(StatutAvenant.ADOPTE);
        assertThat(valide.getDemandeCorrectionId()).as("aucune correction sans impact financier").isNull();
    }

    @Test
    @Transactional
    void chaqueEtapeAppartientASonActeur() {
        preparerCertificat();
        AvenantDto a = service.deposer(depot(TypeAvenant.JURIDIQUE), entreprise);

        assertThatThrownBy(() -> service.prendreEnCharge(a.getId(), null, dgd))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        assertThatThrownBy(() -> service.valider(a.getId(), null, president))
                .as("le Président ne valide qu'après l'examen de la DGTCP")
                .isInstanceOf(ApiException.class);
        service.prendreEnCharge(a.getId(), null, dgtcp);
        assertThatThrownBy(() -> service.valider(a.getId(), null, dgtcp))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
    }

    @Test
    @Transactional
    void leRejetExigeUnMotif() {
        preparerCertificat();
        AvenantDto a = service.deposer(depot(TypeAvenant.CONTRAT), entreprise);

        assertThatThrownBy(() -> service.rejeter(a.getId(), " ", dgtcp)).isInstanceOf(ApiException.class);
        AvenantDto rejete = service.rejeter(a.getId(), "Pièces insuffisantes", dgtcp);

        assertThat(rejete.getStatut()).isEqualTo(StatutAvenant.REJETE);
        assertThat(rejete.getMotifRejet()).isEqualTo("Pièces insuffisantes");
    }

    // ── l'impact financier et la demande de correction ─────────────────────────────────────────

    @Test
    @Transactional
    void laFicheDeLaDemandeDeCorrectionNommeSonAvenant() {
        // Sans ce lien, l'écran retrouvait l'avenant en parcourant toute la liste des avenants.
        preparerCertificat();
        AvenantDto a = avenantFinancierValide();

        var fiche = demandeService.findById(a.getDemandeCorrectionId(), ac);

        assertThat(fiche.getAvenantId()).isEqualTo(a.getId());
        assertThat(fiche.getAvenantNumero()).isEqualTo(a.getNumero());
        assertThat(demandeService.findById(certificat.getDemandeCorrection().getId(), dgtcp).getAvenantId())
                .as("la demande d'origine n'est issue d'aucun avenant").isNull();
    }

    @Test
    @Transactional
    void laValidationDUnAvenantFinancierCreeLaDemandeDeCorrection() {
        preparerCertificat();
        AvenantDto a = avenantFinancierValide();

        assertThat(a.getStatut()).as("en attente de la correction").isEqualTo(StatutAvenant.EN_COURS_TRAITEMENT);
        assertThat(a.getDemandeCorrectionId()).isNotNull();
        DemandeCorrection d = demandeRepository.findById(a.getDemandeCorrectionId()).orElseThrow();
        DemandeCorrection origine = certificat.getDemandeCorrection();
        assertThat(d.getStatut()).isEqualTo(StatutDemande.BROUILLON);
        assertThat(d.getAutoriteContractante().getId()).isEqualTo(origine.getAutoriteContractante().getId());
        assertThat(d.getEntreprise().getId()).isEqualTo(origine.getEntreprise().getId());
        assertThat(d.getConvention().getId()).isEqualTo(origine.getConvention().getId());
        assertThat(d.getCreditExterieur()).isEqualByComparingTo("900");
        assertThat(d.getCreditInterieur()).isEqualByComparingTo("500");
        assertThat(d.getMarche()).as("la demande d'origine garde son marché").isNull();
    }

    @Test
    @Transactional
    void lAdoptionDeLaCorrectionAdopteLAvenant() {
        preparerCertificat();
        AvenantDto a = avenantFinancierValide();
        DemandeCorrection d = enValidationAvecLettre(a.getDemandeCorrectionId());

        demandeService.updateStatut(d.getId(), StatutDemande.ADOPTEE, president, null, true);

        assertThat(avenantRepository.findById(a.getId()).orElseThrow().getStatut()).isEqualTo(StatutAvenant.ADOPTE);
    }

    @Test
    @Transactional
    void leRejetDeLaCorrectionRejetteLAvenant() {
        preparerCertificat();
        AvenantDto a = avenantFinancierValide();
        DemandeCorrection d = enValidationAvecLettre(a.getDemandeCorrectionId());

        demandeService.updateStatut(d.getId(), StatutDemande.REJETEE, president, "Hors champ", true);

        Avenant apres = avenantRepository.findById(a.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutAvenant.REJETE);
        assertThat(apres.getMotifRejet()).contains("Hors champ");
    }

    @Test
    @Transactional
    void laCorrectionDUnAvenantNeMetPasEnPlaceUnNouveauCredit() {
        preparerCertificat();
        AvenantDto a = avenantFinancierValide();
        DemandeCorrection d = demandeRepository.findById(a.getDemandeCorrectionId()).orElseThrow();
        d.setStatut(StatutDemande.ADOPTEE);
        demandeRepository.saveAndFlush(d);

        assertThatThrownBy(() -> certificatService.create(mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest.builder()
                .entrepriseId(d.getEntreprise().getId())
                .demandeCorrectionId(d.getId())
                .build()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("issue d'un avenant");
    }

    // ── l'application ──────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lApplicationRecalculeLesSoldesEnConservantLeConsomme() {
        preparerCertificat();
        Avenant a = avenantAdopte(TypeAvenant.FINANCIER);

        AvenantDto applique = service.appliquer(a.getId(), nouveauRecap(), dgtcp);

        CertificatCredit apres = certificatRepository.findById(certificat.getId()).orElseThrow();
        assertThat(applique.getStatut()).isEqualTo(StatutAvenant.APPLIQUE);
        assertThat(apres.getStatut()).isEqualTo(StatutCertificat.MODIFIE);
        assertThat(apres.getSoldeCordon()).as("900 accordés − 200 consommés").isEqualByComparingTo("700");
        assertThat(apres.getTvaImportationDouane()).as("400 − 50").isEqualByComparingTo("350");
        assertThat(apres.getSoldeTVA()).as("500 − 250").isEqualByComparingTo("250");
        assertThat(apres.getMontantCordon()).isEqualByComparingTo("1300");
        assertThat(applique.getSoldeCordonAvant()).isEqualByComparingTo("500");
        assertThat(applique.getSoldeCordonApres()).isEqualByComparingTo("700");
    }

    @Test
    @Transactional
    void unAvenantNonFinancierAppliqueNeTouchePasAuxMontants() {
        preparerCertificat();
        Avenant a = avenantAdopte(TypeAvenant.JURIDIQUE);

        service.appliquer(a.getId(), null, dgtcp);

        CertificatCredit apres = certificatRepository.findById(certificat.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutCertificat.MODIFIE);
        assertThat(apres.getSoldeCordon()).isEqualByComparingTo("500");
    }

    @Test
    @Transactional
    void uneUtilisationEnCoursBloqueLApplication() {
        preparerCertificat();
        Avenant a = avenantAdopte(TypeAvenant.FINANCIER);
        UtilisationDouaniere u = new UtilisationDouaniere();
        u.setType(TypeUtilisation.DOUANIER);
        u.setStatut(StatutUtilisation.EN_VERIFICATION);
        u.setDateDemande(Instant.now());
        u.setMontant(BigDecimal.TEN);
        u.setCertificatCredit(certificat);
        u.setEntreprise(certificat.getEntreprise());
        u.setNumeroDeclaration("DECL-AV-" + System.nanoTime());
        u.setNumeroBulletin("BUL-AV-" + System.nanoTime());
        utilisationRepository.saveAndFlush(u);

        assertThatThrownBy(() -> service.appliquer(a.getId(), nouveauRecap(), dgtcp))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("en cours");
        assertThat(certificatRepository.findById(certificat.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutCertificat.OUVERT);
    }

    @Test
    @Transactional
    void seulLaDgtcpApplique() {
        preparerCertificat();
        Avenant a = avenantAdopte(TypeAvenant.JURIDIQUE);

        assertThatThrownBy(() -> service.appliquer(a.getId(), null, president))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    /**
     * Le certificat de démonstration, ouvert, aux montants connus : cordon 1000 = b 600 + c 100 +
     * d 300, solde 500 (200 consommés) ; quota d restant 250 ; TVA intérieure 400, solde 150. Ses
     * utilisations seedées sont neutralisées pour qu'aucune ne soit en cours.
     */
    private void preparerCertificat() {
        certificat = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        utilisationRepository.findByCertificatCreditId(certificat.getId()).forEach(u -> {
            u.setStatut(StatutUtilisation.LIQUIDEE);
            utilisationRepository.save(u);
        });
        certificat.setStatut(StatutCertificat.OUVERT);
        certificat.setMontantCordon(bd(1000));
        certificat.setDroitsEtTaxesDouaneHorsTva(bd(600));
        certificat.setTaxesConsommation(bd(100));
        certificat.setTvaImportationDouaneAccordee(bd(300));
        certificat.setTvaImportationDouane(bd(250));
        certificat.setSoldeCordon(bd(500));
        certificat.setTvaCollecteeTravaux(bd(700));
        certificat.setMontantTVAInterieure(bd(400));
        certificat.setSoldeTVA(bd(150));
        certificat.setTransfertCredit(null);
        certificat = certificatRepository.saveAndFlush(certificat);
    }

    private CreateAvenantRequest depot(TypeAvenant type) {
        return CreateAvenantRequest.builder()
                .certificatCreditId(certificat.getId())
                .type(type)
                .motif("Avenant n°1 au marché")
                .build();
    }

    private AvenantDto avenantFinancierValide() {
        CreateAvenantRequest r = depot(TypeAvenant.FINANCIER);
        r.setCreditExterieurPropose(bd(900));
        r.setCreditInterieurPropose(bd(500));
        AvenantDto a = service.deposer(r, entreprise);
        service.prendreEnCharge(a.getId(), null, dgtcp);
        return service.valider(a.getId(), null, president);
    }

    private DemandeCorrection enValidationAvecLettre(Long demandeId) {
        DemandeCorrection d = demandeRepository.findById(demandeId).orElseThrow();
        d.setStatut(StatutDemande.EN_VALIDATION);
        d = demandeRepository.saveAndFlush(d);
        documentRepository.saveAndFlush(Document.builder()
                .codeDocument("LETTRE_ADOPTION")
                .nomFichier("lettre.pdf")
                .chemin("/api/local-files/lettre-av-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(10L)
                .version(1)
                .actif(true)
                .demandeCorrection(d)
                .build());
        return d;
    }

    private Avenant avenantAdopte(TypeAvenant type) {
        return avenantRepository.saveAndFlush(Avenant.builder()
                .numero("AV-TEST-" + System.nanoTime())
                .type(type)
                .statut(StatutAvenant.ADOPTE)
                .motif("Avenant adopté")
                .dateDepot(Instant.now())
                .certificatCredit(certificat)
                .build());
    }

    /** Nouveau crédit : b 800 + c 100 + d 400 = 1300 ; g 900 − d 400 = 500 de TVA intérieure. */
    private ApplicationAvenantRequest nouveauRecap() {
        return ApplicationAvenantRequest.builder()
                .droitsEtTaxesDouaneHorsTva(bd(800))
                .taxesConsommation(bd(100))
                .tvaImportationDouaneAccordee(bd(400))
                .montantCordon(bd(1300))
                .tvaCollecteeTravaux(bd(900))
                .montantTVAInterieure(bd(500))
                .build();
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
