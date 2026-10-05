package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La DGTCP contrôle le dossier et le transmet au Président.
 *
 * <p>Étape intercalée entre la saisie du chèque par l'entreprise et l'émission du certificat : la
 * DGTCP atteste que le bulletin est visé et le chèque certifié saisi avant de présenter le dossier.
 * Ces tests verrouillent l'ordre, les deux contrôles de substance, et le fait que la route de statut
 * générique ne permette pas de court-circuiter l'étape.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste, tout test {@code RANDOM_PORT} y serait une
 * couverture morte.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationControleDgtcpIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired
    private UtilisationCreditService service;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private DocumentUtilisationCreditRepository documentRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    private AuthenticatedUser dgtcp;
    private AuthenticatedUser president;
    private AuthenticatedUser dgd;

    @BeforeEach
    void setUpUsers() {
        dgtcp = new AuthenticatedUser(2L, "dgtcp", Role.DGTCP);
        president = new AuthenticatedUser(1L, "president", Role.PRESIDENT);
        dgd = new AuthenticatedUser(6L, "dgd", Role.DGD);
    }

    // ── le circuit nominal ─────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void chequeSaisi_dgtcpTransmet() {
        UtilisationDouaniere util = dossierPretATransmettre();

        UtilisationCreditDto dto = service.transmettreAuPresident(util.getId(), dgtcp);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.TRANSMISE_AU_PRESIDENT);
    }

    @Test
    @Transactional
    void transmissionIdempotente() {
        UtilisationDouaniere util = dossierPretATransmettre();

        service.transmettreAuPresident(util.getId(), dgtcp);
        UtilisationCreditDto second = service.transmettreAuPresident(util.getId(), dgtcp);

        assertThat(second.getStatut()).isEqualTo(StatutUtilisation.TRANSMISE_AU_PRESIDENT);
    }

    @Test
    @Transactional
    void seuleLaDgtcpTransmet() {
        UtilisationDouaniere util = dossierPretATransmettre();

        assertThatThrownBy(() -> service.transmettreAuPresident(util.getId(), president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(403);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN);
                });
        assertThat(utilisationRepository.findById(util.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutUtilisation.CHEQUE_SAISI);
    }

    // ── l'étape est incontournable ─────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lePresidentNePeutPlusEmettreDepuisChequeSaisi() {
        UtilisationDouaniere util = dossierPretATransmettre();

        assertThatThrownBy(() -> service.emettreCertificatUtilisation(util.getId(), president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));
    }

    @Test
    @Transactional
    void etatEmission_attendDesormaisLaTransmission() {
        UtilisationDouaniere util = dossierPretATransmettre();

        CertificatUtilisationEmissionDto etat = service.etatEmissionCertificat(util.getId(), president);

        assertThat(etat.isEmissible()).isFalse();
        assertThat(etat.getCodeBlocage()).isEqualTo("STATUT_INCOMPATIBLE");
        assertThat(etat.getStatutPrealableAttendu()).isEqualTo(StatutUtilisation.TRANSMISE_AU_PRESIDENT);
    }

    @Test
    @Transactional
    void transmisPuisEmis_leCircuitComplet() {
        UtilisationDouaniere util = dossierPretATransmettre();

        service.transmettreAuPresident(util.getId(), dgtcp);
        UtilisationCreditDto emis = service.emettreCertificatUtilisation(util.getId(), president);

        assertThat(emis.getStatut()).isEqualTo(StatutUtilisation.CERTIFICAT_EMIS);
        assertThat(emis.getNumeroCertificatUtilisation()).matches("^CU-\\d{3}/\\d{4}$");
    }

    @Test
    @Transactional
    void routeStatutGenerique_refuseLaTransmission() {
        UtilisationDouaniere util = dossierPretATransmettre();

        // La DGTCP détient les permissions listées par PATCH /{id}/statut : sans ce garde-fou elle
        // atteindrait le statut sans passer par les contrôles de substance.
        assertThatThrownBy(() ->
                service.updateStatut(util.getId(), StatutUtilisation.TRANSMISE_AU_PRESIDENT, dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION);
                });
    }

    // ── la substance du contrôle ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void sansCheque_transmissionRefusee() {
        UtilisationDouaniere util = dossierPretATransmettre();
        util.setNumeroCheque(null);
        utilisationRepository.save(util);

        assertThatThrownBy(() -> service.transmettreAuPresident(util.getId(), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    @Transactional
    void sansBulletinAnnote_transmissionRefusee() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        util.setNumeroCheque("CHQ-900");
        util.setMontantCheque(BigDecimal.valueOf(500));
        utilisationRepository.save(util);
        // Aucun BULLETIN_ANNOTE déposé : la DGD n'a pas matérialisé sa décision.

        assertThatThrownBy(() -> service.transmettreAuPresident(util.getId(), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    @Transactional
    void statutIncompatible_transmissionRefusee() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.VISE);

        assertThatThrownBy(() -> service.transmettreAuPresident(util.getId(), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(409);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE);
                });
    }

    // ── le bulletin annoté est obligatoire au visa ─────────────────────────────────────────────

    @Test
    @Transactional
    void visaSansBulletin_refuse() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.DEMANDEE);

        assertThatThrownBy(() -> service.visaDgd(util.getId(), "[]", null, dgd))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test
    @Transactional
    void visaSansFichier_maisBulletinDejaDepose_passeLeControleDuFichier() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.VISE);
        deposerBulletinAnnote(util);

        // Le contrôle du fichier est franchi ; l'échec qui suit porte sur les décisions, pas sur le
        // bulletin — c'est ce qui prouve que la ré-annotation reste possible.
        assertThatThrownBy(() -> service.visaDgd(util.getId(), "[]", null, dgd))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .doesNotContain("bulletin annoté"));
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UtilisationDouaniere dossierPretATransmettre() {
        UtilisationDouaniere util = douaneAu(StatutUtilisation.CHEQUE_SAISI);
        util.setNumeroCheque("CHQ-" + System.nanoTime());
        util.setMontantCheque(BigDecimal.valueOf(500));
        util = (UtilisationDouaniere) utilisationRepository.save(util);
        deposerBulletinAnnote(util);
        return util;
    }

    private void deposerBulletinAnnote(UtilisationDouaniere util) {
        documentRepository.save(DocumentUtilisationCredit.builder()
                .codeDocument("BULLETIN_ANNOTE")
                .nomFichier("bulletin-annote.pdf")
                .chemin("/api/local-files/bulletin.pdf")
                .dateUpload(Instant.now())
                .taille(1024L)
                .version(1)
                .actif(true)
                .utilisationCredit(util)
                .build());
    }

    private UtilisationDouaniere douaneAu(StatutUtilisation statut) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(1000));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-DGTCP-" + System.nanoTime());
        util.setNumeroBulletin("BUL-DGTCP-" + System.nanoTime());
        return (UtilisationDouaniere) utilisationRepository.save(util);
    }
}
