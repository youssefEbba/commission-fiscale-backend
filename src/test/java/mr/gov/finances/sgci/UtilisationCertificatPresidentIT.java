package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEmissionDto;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le Président émet le certificat d'utilisation, et il l'émet <b>avant</b> l'étape de paiement.
 *
 * <p>Le certificat est la pièce que l'entreprise présente au Trésor (douane) ou à la DGI (TVA
 * intérieure) pour obtenir sa quittance : il doit donc exister avant cette présentation. Ces tests
 * verrouillent cet ordre, l'idempotence de l'émission, la fermeture de la route de statut générique,
 * et le fait que le calcul financier de la DGTCP ne numérote plus rien.
 *
 * <p>Niveau service, et non HTTP : Tomcat ne démarre pas sur ce poste, ce qui rendrait tout test
 * {@code RANDOM_PORT} inexploitable.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationCertificatPresidentIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired
    private UtilisationCreditService service;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    private AuthenticatedUser president;
    private AuthenticatedUser dgtcp;
    private AuthenticatedUser dgi;
    private AuthenticatedUser entreprise;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUpUsers() {
        president = new AuthenticatedUser(1L, "president", Role.PRESIDENT);
        dgtcp = new AuthenticatedUser(2L, "dgtcp", Role.DGTCP);
        dgi = new AuthenticatedUser(5L, "dgi", Role.DGI);
        entreprise = new AuthenticatedUser(3L, "entreprise", Role.ENTREPRISE);
        admin = new AuthenticatedUser(4L, "admin", Role.ADMIN_SI);
    }

    // ── l'émission, et sa place dans le circuit ────────────────────────────────────────────────

    @Test
    @Transactional
    void douaneChequeSaisi_presidentEmet_numeroAttribueEtStatutCertificatEmis() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        UtilisationCreditDto dto = service.emettreCertificatUtilisation(util.getId(), president);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CERTIFICAT_EMIS);
        assertThat(dto.getNumeroCertificatUtilisation()).matches("^CU-\\d{3}/\\d{4}$");
        assertThat(dto.getDateCertificatUtilisation()).isNotNull();
    }

    @Test
    @Transactional
    void tvaValidee_presidentEmet_avantLaQuittanceDgi() {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.VALIDEE);

        UtilisationCreditDto dto = service.emettreCertificatUtilisation(util.getId(), president);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CERTIFICAT_EMIS);
        assertThat(dto.getNumeroCertificatUtilisation()).matches("^CU-\\d{3}/\\d{4}$");
    }

    @Test
    @Transactional
    void seulLePresidentEmet() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        assertThatThrownBy(() -> service.emettreCertificatUtilisation(util.getId(), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(403);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN);
                });
        assertThat(utilisationRepository.findById(util.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutUtilisation.CHEQUE_SAISI);
    }

    @Test
    @Transactional
    void emissionIdempotente_secondAppelRendLeMemeNumero() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        String premier = service.emettreCertificatUtilisation(util.getId(), president)
                .getNumeroCertificatUtilisation();
        String second = service.emettreCertificatUtilisation(util.getId(), president)
                .getNumeroCertificatUtilisation();

        assertThat(second).isEqualTo(premier);
    }

    @Test
    @Transactional
    void etapePrealableNonAtteinte_emissionRefusee() {
        // Le bulletin est visé mais l'entreprise n'a pas encore remis son chèque.
        UtilisationDouaniere util = douaneAu(StatutUtilisation.VISE);

        assertThatThrownBy(() -> service.emettreCertificatUtilisation(util.getId(), president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));
    }

    @Test
    @Transactional
    void routeStatutGenerique_refuseCertificatEmis() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        assertThatThrownBy(() ->
                service.updateStatut(util.getId(), StatutUtilisation.CERTIFICAT_EMIS, president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION);
                });
    }

    // ── l'ordre imposé : le certificat précède le paiement ─────────────────────────────────────

    @Test
    @Transactional
    void envoiAuTresorRefuseTantQueLeCertificatNestPasEmis() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        util.setNumeroCheque("CHQ-001");
        utilisationRepository.save(util);

        assertThatThrownBy(() -> service.envoyerAuTresor(util.getId(), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(409);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS);
                });
    }

    @Test
    @Transactional
    void certificatEmis_puisEnvoiAuTresorAccepte() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        util.setNumeroCheque("CHQ-002");
        utilisationRepository.save(util);

        service.emettreCertificatUtilisation(util.getId(), president);
        UtilisationCreditDto dto = service.envoyerAuTresor(util.getId(), dgtcp);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.ENVOYEE_AU_TRESOR);
        assertThat(dto.getNumeroCertificatUtilisation()).isNotBlank();
    }

    @Test
    @Transactional
    void quittanceDgiRefuseeTantQueLeCertificatNestPasEmis() throws Exception {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.VALIDEE);

        assertThatThrownBy(() -> service.deposerQuittanceDgi(
                util.getId(), "Q-001", Instant.now(), BigDecimal.valueOf(50), null, dgi))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS));
    }

    @Test
    @Transactional
    void certificatEmis_puisQuittanceDgiAcceptee() throws Exception {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.VALIDEE);
        service.emettreCertificatUtilisation(util.getId(), president);

        // Le justificatif est obligatoire au premier dépôt : c'est la pièce qui atteste le paiement.
        MockMultipartFile justificatif = new MockMultipartFile(
                "file", "quittance.pdf", "application/pdf", "quittance".getBytes());
        UtilisationCreditDto dto = service.deposerQuittanceDgi(
                util.getId(), "Q-002", Instant.now(), BigDecimal.valueOf(50), justificatif, dgi);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);
    }

    // ── le calcul de la DGTCP ne numérote plus ─────────────────────────────────────────────────

    @Test
    @Transactional
    void apurementTva_neNumerotePlus() {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);

        UtilisationCreditDto apure = service.apurerTVAInterieure(util.getId(), null, dgtcp);

        assertThat(apure.getStatut()).isEqualTo(StatutUtilisation.APUREE);
        // Le cœur du chantier : le calcul de la DGTCP ne produit plus le certificat.
        assertThat(apure.getNumeroCertificatUtilisation()).isNull();
    }

    @Test
    @Transactional
    void leCalculDgtcpMarqueLeDossierCommeSoumisALemission() {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);

        service.apurerTVAInterieure(util.getId(), null, dgtcp);

        // Sans ce marqueur, le rattrapage au démarrage dispenserait ce dossier à chaque redémarrage.
        assertThat(utilisationRepository.findById(util.getId()).orElseThrow()
                .getEmissionCertificatRequise()).isTrue();
    }

    // ── le verrou de clôture ───────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void clotureRefuseeTantQueLeCertificatNestPasEmis() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.LIQUIDEE);

        assertThatThrownBy(() -> service.cloturerReceptionEntreprise(util.getId(), entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(409);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS);
                });
    }

    @Test
    @Transactional
    void certificatEmis_clotureAcceptee() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.LIQUIDEE);
        util.setNumeroCertificatUtilisation("CU-042/2026");
        utilisationRepository.save(util);

        UtilisationCreditDto dto = service.cloturerReceptionEntreprise(util.getId(), entreprise);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    @Test
    @Transactional
    void repriseArchive_clotureAcceptee_sansEmission() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.LIQUIDEE);
        util.setOrigineArchiveLibelle("UT 1 | 1/01/2019");
        utilisationRepository.save(util);

        UtilisationCreditDto dto = service.cloturerReceptionEntreprise(util.getId(), entreprise);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    @Test
    @Transactional
    void dossierHistorique_clotureAcceptee_marqueurDeGrandfathering() {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.APUREE);
        util.setEmissionCertificatRequise(Boolean.FALSE);
        utilisationRepository.save(util);

        UtilisationCreditDto dto = service.cloturerReceptionEntreprise(util.getId(), entreprise);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    // ── substitution administrative ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void substitutionAdmin_motifObligatoire() throws Exception {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        assertThatThrownBy(() ->
                service.adminEmettreCertificatUtilisation(util.getId(), "  ", null, admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        UtilisationCreditDto dto = service.adminEmettreCertificatUtilisation(
                util.getId(), "Président indisponible, session du 12/09", null, admin);
        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CERTIFICAT_EMIS);
        assertThat(dto.getNumeroCertificatUtilisation()).matches("^CU-\\d{3}/\\d{4}$");
    }

    @Test
    @Transactional
    void substitutionAdmin_refuseeAuxAutresRoles() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);

        assertThatThrownBy(() ->
                service.adminEmettreCertificatUtilisation(util.getId(), "motif", null, dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(403));
    }

    // ── écran d'état ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void etatEmission_exposeLeMemeCodeQueLecriture() {
        UtilisationDouaniere avant = douaneAu(StatutUtilisation.VISE);
        CertificatUtilisationEmissionDto bloque = service.etatEmissionCertificat(avant.getId(), president);
        assertThat(bloque.isEmissible()).isFalse();
        assertThat(bloque.getCodeBlocage()).isEqualTo("STATUT_INCOMPATIBLE");
        assertThat(bloque.getMotifBlocage()).isNotBlank();
        assertThat(bloque.getStatutPrealableAttendu()).isEqualTo(StatutUtilisation.CHEQUE_SAISI);

        UtilisationDouaniere prete = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        CertificatUtilisationEmissionDto ok = service.etatEmissionCertificat(prete.getId(), president);
        assertThat(ok.isEmissible()).isTrue();
        assertThat(ok.getCodeBlocage()).isNull();
        assertThat(ok.isCertificatSigneDepose()).isFalse();

        // Pour le DGTCP, la cause du blocage est le rôle, pas le statut.
        CertificatUtilisationEmissionDto vuDgtcp = service.etatEmissionCertificat(prete.getId(), dgtcp);
        assertThat(vuDgtcp.isEmissible()).isFalse();
        assertThat(vuDgtcp.getCodeBlocage()).isEqualTo("ROLE_NON_HABILITE");
    }

    @Test
    @Transactional
    void etatEmission_tvaAttendLaValidation() {
        UtilisationTVAInterieure util = tvaAu(StatutUtilisation.EN_VERIFICATION);

        CertificatUtilisationEmissionDto etat = service.etatEmissionCertificat(util.getId(), president);

        assertThat(etat.isEmissible()).isFalse();
        assertThat(etat.getStatutPrealableAttendu()).isEqualTo(StatutUtilisation.VALIDEE);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private CertificatCredit certificat() {
        return certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));
    }

    private UtilisationDouaniere douaneAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        Entreprise ent = cert.getEntreprise();

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(ent);
        util.setNumeroDeclaration("DECL-CU-" + System.nanoTime());
        util.setNumeroBulletin("BUL-CU-" + System.nanoTime());
        return (UtilisationDouaniere) utilisationRepository.save(util);
    }

    private UtilisationTVAInterieure tvaAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        Entreprise ent = cert.getEntreprise();

        UtilisationTVAInterieure util = new UtilisationTVAInterieure();
        util.setType(TypeUtilisation.TVA_INTERIEURE);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(50));
        util.setMontantTVA(BigDecimal.valueOf(50));
        util.setNumeroFacture("FAC-CU-" + System.nanoTime());
        util.setDateFacture(Instant.now());
        util.setCertificatCredit(cert);
        util.setEntreprise(ent);
        return (UtilisationTVAInterieure) utilisationRepository.save(util);
    }

    @SuppressWarnings("unused")
    private UtilisationCredit reload(Long id) {
        return utilisationRepository.findById(id).orElseThrow();
    }
}
