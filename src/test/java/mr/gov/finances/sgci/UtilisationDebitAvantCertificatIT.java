package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.ModificationMontantsPresidentRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
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
 * Le crédit est débité avant le certificat d'utilisation, et le Président peut corriger les montants
 * avant de l'émettre : le certificat porte ainsi les montants définitifs et les soldes après débit.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationDebitAvantCertificatIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private UtilisationCreditService service;
    @Autowired private DecisionUtilisationCreditService decisionService;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private DocumentUtilisationCreditRepository documentRepository;
    @Autowired private TvaDeductibleStockRepository stockRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgtcp;
    private AuthenticatedUser president;
    private CertificatCredit cert;

    @BeforeEach
    void setUp() {
        dgtcp = compte("dgtcp");
        president = compte("president");
    }

    // ── douane ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void douane_liquidee_transmise_reduite_puisCertificatEtCloture() {
        preparerCredit();
        UtilisationDouaniere u = douaneLiquidable(BigDecimal.valueOf(1000), BigDecimal.valueOf(300));

        service.liquiderDouane(u.getId(), dgtcp);
        assertThat(credit().getSoldeCordon()).isEqualByComparingTo("9300");          // 10 000 − (1 000 − 300)
        assertThat(credit().getTvaImportationDouane()).isEqualByComparingTo("4700");  // 5 000 − 300

        assertThat(service.transmettreAuPresident(u.getId(), dgtcp).getStatut())
                .isEqualTo(StatutUtilisation.TRANSMISE_AU_PRESIDENT);

        // Le Président ramène les droits de 700 à 600 et la TVA de 300 à 250.
        UtilisationCreditDto corrige = service.modifierMontantsPresident(u.getId(),
                ModificationMontantsPresidentRequest.builder().droitsImputes(BigDecimal.valueOf(600))
                        .tvaImputee(BigDecimal.valueOf(250)).motif("Ligne 3 hors périmètre").build(), president);

        assertThat(corrige.getTotalPrisEnCharge()).isEqualByComparingTo("850");
        assertThat(corrige.getMontantImputeAvantModification()).isEqualByComparingTo("1000");
        assertThat(corrige.getMotifModificationPresident()).isEqualTo("Ligne 3 hors périmètre");
        assertThat(credit().getSoldeCordon()).isEqualByComparingTo("9400");
        assertThat(credit().getTvaImportationDouane()).isEqualByComparingTo("4750");
        assertThat(stockDe(u).getMontantRestant()).isEqualByComparingTo("250");
        assertThat(corrige.getSoldeCordonApres()).isEqualByComparingTo("9400");

        // Augmenter est refusé : la part à payer est déjà encaissée.
        assertThatThrownBy(() -> service.modifierMontantsPresident(u.getId(),
                ModificationMontantsPresidentRequest.builder().droitsImputes(BigDecimal.valueOf(700))
                        .motif("essai").build(), president))
                .isInstanceOf(ApiException.class).hasMessageContaining("réduit");

        emettre(u);
        assertThat(service.cloturer(u.getId(), null, dgtcp).getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    @Test
    @Transactional
    void douane_tvaDejaDeduite_neSeReduitPlus() {
        preparerCredit();
        UtilisationDouaniere u = douaneLiquidable(BigDecimal.valueOf(1000), BigDecimal.valueOf(300));
        service.liquiderDouane(u.getId(), dgtcp);
        service.transmettreAuPresident(u.getId(), dgtcp);
        TvaDeductibleStock stock = stockDe(u);
        stock.setMontantRestant(BigDecimal.valueOf(20));   // déjà déduite d'une TVA intérieure
        stockRepository.save(stock);

        assertThatThrownBy(() -> service.modifierMontantsPresident(u.getId(),
                ModificationMontantsPresidentRequest.builder().tvaImputee(BigDecimal.valueOf(200))
                        .motif("essai").build(), president))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà été déduite");
    }

    @Test
    @Transactional
    void leChequeSaisi_partAuTresorSansCertificat_etLePresidentAttendLeDebit() {
        preparerCredit();
        UtilisationDouaniere u = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        u.setNumeroCheque("CHQ-" + System.nanoTime());
        u.setMontantCheque(BigDecimal.valueOf(100));
        utilisationRepository.save(u);

        assertThatThrownBy(() -> service.transmettreAuPresident(u.getId(), dgtcp)).isInstanceOf(ApiException.class);
        assertThat(service.envoyerAuTresor(u.getId(), dgtcp).getStatut()).isEqualTo(StatutUtilisation.ENVOYEE_AU_TRESOR);
    }

    // ── TVA intérieure ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void tva_apuree_transmise_corrigee_puisCertificat() {
        preparerCredit();
        UtilisationTVAInterieure u = tvaAu(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE, BigDecimal.valueOf(400));

        service.apurerTVAInterieure(u.getId(), null, dgtcp);
        assertThat(credit().getSoldeTVA()).isEqualByComparingTo("1600");   // 2 000 − 400 (aucun stock déductible)
        service.transmettreAuPresident(u.getId(), dgtcp);

        UtilisationCreditDto corrige = service.modifierMontantsPresident(u.getId(),
                ModificationMontantsPresidentRequest.builder().montantTVA(BigDecimal.valueOf(300))
                        .motif("Facture partiellement éligible").build(), president);

        assertThat(corrige.getTvaNette()).isEqualByComparingTo("300");
        assertThat(corrige.getCreditInterieurUtilise()).isEqualByComparingTo("300");
        assertThat(corrige.getSoldeTVAApres()).isEqualByComparingTo("1700");
        assertThat(corrige.getMontantImputeAvantModification()).isEqualByComparingTo("400");
        assertThat(credit().getSoldeTVA()).isEqualByComparingTo("1700");

        emettre(u);
        assertThat(service.cloturer(u.getId(), null, dgtcp).getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    // ── garde-fous ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laCorrection_reserveeAuPresident_avecMotif_surUnDossierTransmis() {
        preparerCredit();
        UtilisationDouaniere u = douaneLiquidable(BigDecimal.valueOf(1000), BigDecimal.valueOf(300));
        service.liquiderDouane(u.getId(), dgtcp);
        ModificationMontantsPresidentRequest req = ModificationMontantsPresidentRequest.builder()
                .droitsImputes(BigDecimal.valueOf(600)).motif("motif").build();

        assertThatThrownBy(() -> service.modifierMontantsPresident(u.getId(), req, president))
                .as("pas encore transmis").isInstanceOf(ApiException.class);
        service.transmettreAuPresident(u.getId(), dgtcp);
        assertThatThrownBy(() -> service.modifierMontantsPresident(u.getId(), req, dgtcp))
                .as("la DGTCP ne corrige pas").isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.modifierMontantsPresident(u.getId(),
                ModificationMontantsPresidentRequest.builder().droitsImputes(BigDecimal.valueOf(600)).motif(" ").build(),
                president)).as("motif obligatoire").isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void unDossierDebite_neSeRejettePlus() {
        preparerCredit();
        UtilisationDouaniere u = douaneLiquidable(BigDecimal.valueOf(1000), BigDecimal.valueOf(300));
        service.liquiderDouane(u.getId(), dgtcp);
        service.transmettreAuPresident(u.getId(), dgtcp);

        assertThatThrownBy(() -> decisionService.saveDecision(u.getId(), DecisionCorrectionType.REJET_TEMP,
                "pièce manquante", null, dgtcp))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà débité");
        assertThat(utilisationRepository.findById(u.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutUtilisation.TRANSMISE_AU_PRESIDENT);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private void preparerCredit() {
        cert = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        cert.setStatut(StatutCertificat.OUVERT);
        cert.setSoldeCordon(BigDecimal.valueOf(10000));
        cert.setTvaImportationDouane(BigDecimal.valueOf(5000));
        cert.setSoldeTVA(BigDecimal.valueOf(2000));
        certificatRepository.saveAndFlush(cert);
        stockRepository.findByCertificatCreditIdOrderByDateCreationAsc(cert.getId()).forEach(s -> {
            s.setMontantRestant(BigDecimal.ZERO);
            stockRepository.save(s);
        });
    }

    private CertificatCredit credit() {
        return certificatRepository.findById(cert.getId()).orElseThrow();
    }

    private TvaDeductibleStock stockDe(UtilisationDouaniere u) {
        return stockRepository.findByCertificatCreditIdOrderByDateCreationAsc(cert.getId()).stream()
                .filter(s -> s.getUtilisationDouane() != null && u.getId().equals(s.getUtilisationDouane().getId()))
                .findFirst().orElseThrow();
    }

    /** Quittances du Trésor enregistrées, visa DGD posé (total pris en charge et TVA), bulletin annoté. */
    private UtilisationDouaniere douaneLiquidable(BigDecimal total, BigDecimal tva) {
        UtilisationDouaniere u = douaneAu(StatutUtilisation.QUITTANCES_ENREGISTREES);
        u.setTotalPrisEnCharge(total);
        u.setMontantTVA(tva);
        u.setNumeroCheque("CHQ-" + System.nanoTime());
        u.setMontantCheque(BigDecimal.valueOf(100));
        u = (UtilisationDouaniere) utilisationRepository.save(u);
        document(u, "BULLETIN_ANNOTE");
        return u;
    }

    private UtilisationDouaniere douaneAu(StatutUtilisation statut) {
        UtilisationDouaniere u = new UtilisationDouaniere();
        u.setType(TypeUtilisation.DOUANIER);
        u.setStatut(statut);
        u.setDateDemande(Instant.now());
        u.setMontant(BigDecimal.valueOf(1000));
        u.setCertificatCredit(cert);
        u.setEntreprise(cert.getEntreprise());
        u.setNumeroDeclaration("DECL-DEB-" + System.nanoTime());
        return (UtilisationDouaniere) utilisationRepository.save(u);
    }

    private UtilisationTVAInterieure tvaAu(StatutUtilisation statut, BigDecimal tva) {
        UtilisationTVAInterieure u = new UtilisationTVAInterieure();
        u.setType(TypeUtilisation.TVA_INTERIEURE);
        u.setStatut(statut);
        u.setDateDemande(Instant.now());
        u.setMontant(tva);
        u.setMontantTVA(tva);
        u.setNumeroFacture("FAC-DEB-" + System.nanoTime());
        u.setDateFacture(Instant.now());
        u.setCertificatCredit(cert);
        u.setEntreprise(cert.getEntreprise());
        return (UtilisationTVAInterieure) utilisationRepository.save(u);
    }

    /** Numéro réservé, certificat signé déposé, émission (anomalie A34). */
    private void emettre(UtilisationCredit u) {
        service.reserverNumeroCertificatUtilisation(u.getId(), president);
        document(u, "CERTIFICAT_UTILISATION");
        assertThat(service.emettreCertificatUtilisation(u.getId(), president).getStatut())
                .isEqualTo(StatutUtilisation.CERTIFICAT_EMIS);
    }

    private void document(UtilisationCredit u, String code) {
        documentRepository.save(DocumentUtilisationCredit.builder()
                .codeDocument(code).nomFichier(code.toLowerCase() + ".pdf").chemin("/api/local-files/" + code)
                .dateUpload(Instant.now()).taille(1024L).version(1).actif(true).utilisationCredit(u).build());
    }

    private AuthenticatedUser compte(String username) {
        var u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }
}
