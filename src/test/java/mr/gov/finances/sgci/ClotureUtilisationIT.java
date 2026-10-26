package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.UtilisationCreditService;
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
 * La DGTCP clôture l'utilisation, une fois le crédit débité — et plus l'entreprise.
 *
 * <p>L'équipe de test l'a relevé : « le crédit est clôturé par la DGTCP, pas par l'entreprise ».
 * L'ancien accusé de réception de l'entreprise était de surcroît permis dès l'émission du certificat,
 * avant la liquidation : clôturée à ce stade, l'utilisation ne débitait jamais le solde du crédit.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClotureUtilisationIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private UtilisationCreditService service;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgtcp;
    private AuthenticatedUser entreprise;
    private AuthenticatedUser relais;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        dgtcp = compte("dgtcp");
        entreprise = compte("entreprise");
        relais = compte("commission_relais");
        admin = compte("admin");
    }

    // ── la DGTCP clôture, crédit débité ────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laDgtcpClotureUneUtilisationDouaniereLiquidee() {
        UtilisationCredit util = douaneAu(StatutUtilisation.LIQUIDEE);

        UtilisationCreditDto dto = service.cloturer(util.getId(), null, dgtcp);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    @Test
    @Transactional
    void laDgtcpClotureUneUtilisationTvaApuree() {
        UtilisationCredit util = tvaAu(StatutUtilisation.APUREE);

        UtilisationCreditDto dto = service.cloturer(util.getId(), null, dgtcp);

        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    @Test
    @Transactional
    void pasDeClotureAvantLeDebitDuCredit() {
        // La faille d'origine : certificat émis, rien de débité, et pourtant clôturable.
        for (StatutUtilisation avant : new StatutUtilisation[]{StatutUtilisation.CERTIFICAT_EMIS,
                StatutUtilisation.ENVOYEE_AU_TRESOR, StatutUtilisation.QUITTANCES_ENREGISTREES}) {
            UtilisationCredit util = douaneAu(avant);

            assertThatThrownBy(() -> service.cloturer(util.getId(), null, dgtcp))
                    .as(avant.name())
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));
            assertThat(utilisationRepository.findById(util.getId()).orElseThrow().getStatut()).isEqualTo(avant);
        }
        UtilisationCredit tva = tvaAu(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);
        assertThatThrownBy(() -> service.cloturer(tva.getId(), null, dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));
    }

    // ── l'entreprise ne clôture plus ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lEntrepriseEtLaCommissionRelaisNeCloturentPlus() {
        UtilisationCredit util = douaneAu(StatutUtilisation.LIQUIDEE);

        for (AuthenticatedUser deposant : new AuthenticatedUser[]{entreprise, relais}) {
            assertThatThrownBy(() -> service.cloturer(util.getId(), null, deposant))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        }
        assertThat(utilisationRepository.findById(util.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutUtilisation.LIQUIDEE);
    }

    @Test
    @Transactional
    void laRouteDeStatutGeneriqueNOffrePasDeSecondeCloture() {
        UtilisationCredit avantLiquidation = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);
        UtilisationCredit liquidee = douaneAu(StatutUtilisation.LIQUIDEE);

        assertThatThrownBy(() -> service.updateStatut(avantLiquidation.getId(), StatutUtilisation.CLOTUREE, entreprise))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateStatut(liquidee.getId(), StatutUtilisation.CLOTUREE, entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));

        // Le cas que seul le renvoi vers cloturer() ferme : la DGTCP elle-même, avant le débit.
        assertThatThrownBy(() -> service.updateStatut(avantLiquidation.getId(), StatutUtilisation.CLOTUREE, dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));

        UtilisationCreditDto dto = service.updateStatut(liquidee.getId(), StatutUtilisation.CLOTUREE, dgtcp);
        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    // ── la substitution administrative ─────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lAdministrateurClotureAvecUnMotif() {
        UtilisationCredit util = douaneAu(StatutUtilisation.LIQUIDEE);

        assertThatThrownBy(() -> service.cloturer(util.getId(), " ", admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        UtilisationCreditDto dto = service.cloturer(util.getId(), "Agent DGTCP absent", admin);
        assertThat(dto.getStatut()).isEqualTo(StatutUtilisation.CLOTUREE);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private CertificatCredit certificat() {
        return certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));
    }

    /** Une utilisation douanière au statut donné, au certificat d'utilisation émis. */
    private UtilisationCredit douaneAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-CLO-" + System.nanoTime());
        util.setNumeroBulletin("BUL-CLO-" + System.nanoTime());
        util.setNumeroCertificatUtilisation("CU-900/2026");
        util.setDateCertificatUtilisation(Instant.now());
        return utilisationRepository.save(util);
    }

    /** Une utilisation de TVA intérieure au statut donné, au certificat d'utilisation émis. */
    private UtilisationCredit tvaAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        UtilisationTVAInterieure util = new UtilisationTVAInterieure();
        util.setType(TypeUtilisation.TVA_INTERIEURE);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(50));
        util.setMontantTVA(BigDecimal.valueOf(50));
        util.setNumeroFacture("FAC-CLO-" + System.nanoTime());
        util.setDateFacture(Instant.now());
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroCertificatUtilisation("CU-901/2026");
        util.setDateCertificatUtilisation(Instant.now());
        return utilisationRepository.save(util);
    }
}
