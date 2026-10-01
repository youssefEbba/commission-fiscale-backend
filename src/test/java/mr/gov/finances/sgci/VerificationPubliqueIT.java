package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.service.VerificationPubliqueService;
import mr.gov.finances.sgci.web.dto.VerificationPubliqueDto;
import mr.gov.finances.sgci.web.support.VerificationPubliqueRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérification publique d'un document par son QR code.
 *
 * <p>Le test central est {@link #aucuneDonneeFinanciereNestExposee()} : cet endpoint est le seul
 * ouvert sans authentification, et ce qui rend cette ouverture acceptable est que la réponse ne
 * porte aucun montant. Si quelqu'un ajoute un jour un solde au DTO, ce test doit échouer.
 */
@SpringBootTest
@ActiveProfiles("test")
class VerificationPubliqueIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired
    private VerificationPubliqueService service;

    @Autowired
    private VerificationPubliqueRateLimiter rateLimiter;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Test
    void aucuneDonneeFinanciereNestExposee() {
        // Le DTO est le contrat de confidentialité de cet endpoint ouvert : on l'inspecte lui-même,
        // plutôt que de se fier à une instance où les champs seraient nuls par hasard.
        for (Field f : VerificationPubliqueDto.class.getDeclaredFields()) {
            assertThat(f.getType())
                    .as("Le champ « %s » expose un montant sur un endpoint public", f.getName())
                    .isNotEqualTo(BigDecimal.class);
            assertThat(f.getName().toLowerCase())
                    .as("Le nom du champ « %s » suggère une donnée financière", f.getName())
                    .doesNotContain("montant")
                    .doesNotContain("solde")
                    .doesNotContain("credit")
                    .doesNotContain("tva");
        }
    }

    @Test
    @Transactional
    void certificatUtilisation_reconnuEtAtteste() {
        UtilisationDouaniere util = douaneEmise("CU-901/2026");

        VerificationPubliqueDto dto = service.verifier("CU-901/2026");

        assertThat(dto.isTrouve()).isTrue();
        assertThat(dto.getTypeDocument()).isEqualTo(VerificationPubliqueService.TYPE_CERTIFICAT_UTILISATION);
        assertThat(dto.isAuthentique()).isTrue();
        assertThat(dto.getSeveriteUi()).isEqualTo("success");
        assertThat(dto.getEntrepriseRaisonSociale()).isNotBlank();
        assertThat(dto.getStatut()).isEqualTo("CERTIFICAT_EMIS");
        assertThat(util.getNumeroCertificatUtilisation()).isEqualTo("CU-901/2026");
    }

    @Test
    @Transactional
    void utilisationRejetee_certificatNeVautPlusTitre() {
        UtilisationDouaniere util = douaneEmise("CU-902/2026");
        util.setStatut(StatutUtilisation.REJETEE);
        utilisationRepository.save(util);

        VerificationPubliqueDto dto = service.verifier("CU-902/2026");

        assertThat(dto.isTrouve()).isTrue();
        assertThat(dto.isAuthentique()).isFalse();
        assertThat(dto.getSeveriteUi()).isEqualTo("destructive");
    }

    @Test
    @Transactional
    void certificatDeCredit_reconnuParSonNumero() {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();

        VerificationPubliqueDto dto = service.verifier(DEMO_CERTIFICAT);

        assertThat(dto.isTrouve()).isTrue();
        assertThat(dto.getTypeDocument()).isEqualTo(VerificationPubliqueService.TYPE_CERTIFICAT_CREDIT);
        assertThat(dto.getStatut()).isEqualTo(cert.getStatut().name());
        assertThat(dto.getEntrepriseRaisonSociale()).isNotBlank();
    }

    @Test
    void codeInconnu_repondSansErreur() {
        VerificationPubliqueDto dto = service.verifier("CU-000/1900");

        assertThat(dto.isTrouve()).isFalse();
        assertThat(dto.isAuthentique()).isFalse();
        assertThat(dto.getSeveriteUi()).isEqualTo("destructive");
        assertThat(dto.getMotifs()).isNotEmpty();
        // Pas d'exception : un scanner doit recevoir un résultat lisible.
    }

    @Test
    void codeVideOuNul_traiteCommeIntrouvable() {
        assertThat(service.verifier(null).isTrouve()).isFalse();
        assertThat(service.verifier("   ").isTrouve()).isFalse();
    }

    @Test
    @Transactional
    void codeNormalise_casseEtEspacesIndifferents() {
        douaneEmise("CU-903/2026");

        assertThat(service.verifier("  cu-903/2026  ").isTrouve()).isTrue();
    }

    @Test
    void debitLimiteParAdresse() {
        String ip = "203.0.113." + (System.nanoTime() % 200);
        for (int i = 0; i < 30; i++) {
            assertThat(rateLimiter.autorise(ip)).as("appel %d", i + 1).isTrue();
        }
        assertThat(rateLimiter.autorise(ip)).as("31e appel").isFalse();

        // Une autre adresse n'est pas affectée.
        assertThat(rateLimiter.autorise(ip + ".9")).isTrue();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UtilisationDouaniere douaneEmise(String numeroCu) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(StatutUtilisation.CERTIFICAT_EMIS);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-QR-" + System.nanoTime());
        util.setNumeroBulletin("BUL-QR-" + System.nanoTime());
        util.setNumeroCertificatUtilisation(numeroCu);
        util.setDateCertificatUtilisation(Instant.now());
        return (UtilisationDouaniere) utilisationRepository.save(util);
    }
}
