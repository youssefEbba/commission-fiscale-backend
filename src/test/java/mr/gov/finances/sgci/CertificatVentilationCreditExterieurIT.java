package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les taxes de consommation sont une part du crédit extérieur, et la ventilation doit tomber juste.
 *
 * <p>Un crédit extérieur de 200 peut se ventiler en 100 de droits et taxes de douane, 50 de TVA à
 * l'import et 50 de taxes de consommation. Le DGTCP annote ces lignes avant d'ouvrir le certificat :
 * si leur somme ne restitue pas l'enveloppe, le dossier est refusé.
 *
 * <p>Les cas qui comptent sont {@link #sansTvaImport_refuse()} et {@link #sansDroits_refuse()} : le
 * contrôle précédent ne s'exécutait que si ces deux lignes étaient renseignées, si bien qu'en laisser
 * une vide le désactivait entièrement — et des certificats ouverts en portent la trace.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class CertificatVentilationCreditExterieurIT {

    private static final BigDecimal CREDIT_EXTERIEUR = BigDecimal.valueOf(200);

    @Autowired
    private CertificatCreditService service;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgtcp;

    @BeforeEach
    void setUpUser() {
        Utilisateur agent = utilisateurRepository.findByUsername("dgtcp").orElseThrow();
        dgtcp = new AuthenticatedUser(agent.getId(), agent.getUsername(), agent.getRole());
    }

    // ── la ventilation exacte ──────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void ventilationExacte_acceptee() {
        CertificatCredit cert = certificatNeuf();

        CertificatCreditDto dto = service.updateMontants(cert.getId(),
                ventilation(BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(50)), dgtcp);

        assertThat(dto.getMontantCordon()).isEqualByComparingTo(CREDIT_EXTERIEUR);
        assertThat(dto.getCreditExterieurRecap()).isEqualByComparingTo(CREDIT_EXTERIEUR);
    }

    @Test
    @Transactional
    void sommeInexacte_refusee() {
        CertificatCredit cert = certificatNeuf();

        // 100 + 50 + 40 = 190 pour une enveloppe de 200.
        assertThatThrownBy(() -> service.updateMontants(cert.getId(),
                ventilation(BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(40)), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION);
                    assertThat(api.getMessage()).contains("Écart de 10");
                });
    }

    @Test
    @Transactional
    void taxesDeConsommationAbsentes_luesCommeZero() {
        CertificatCredit cert = certificatNeuf();

        // La plupart des marchés n'en comportent pas : 150 + 50 doit suffire.
        CertificatCreditDto dto = service.updateMontants(cert.getId(),
                ventilation(BigDecimal.valueOf(150), BigDecimal.valueOf(50), null), dgtcp);

        assertThat(dto.getCreditExterieurRecap()).isEqualByComparingTo(CREDIT_EXTERIEUR);
    }

    @Test
    @Transactional
    void ecartDarrondi_tolere() {
        CertificatCredit cert = certificatNeuf();

        // La tolérance absorbe l'arrondi de saisie, pas une erreur de ventilation.
        CertificatCreditDto dto = service.updateMontants(cert.getId(),
                ventilation(new BigDecimal("99.50"), BigDecimal.valueOf(50), BigDecimal.valueOf(50)), dgtcp);

        assertThat(dto.getMontantCordon()).isEqualByComparingTo(CREDIT_EXTERIEUR);
    }

    // ── le trou refermé : une ligne vide ne dispense pas du contrôle ───────────────────────────

    @Test
    @Transactional
    void sansTvaImport_refuse() {
        CertificatCredit cert = certificatNeuf();

        assertThatThrownBy(() -> service.updateMontants(cert.getId(),
                ventilation(BigDecimal.valueOf(100), null, BigDecimal.valueOf(50)), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.BUSINESS_RULE_VIOLATION);
                    assertThat(api.getMessage()).contains("TVA à l'import n'est pas renseignée");
                });
    }

    @Test
    @Transactional
    void sansDroits_refuse() {
        CertificatCredit cert = certificatNeuf();

        assertThatThrownBy(() -> service.updateMontants(cert.getId(),
                ventilation(null, BigDecimal.valueOf(50), BigDecimal.valueOf(50)), dgtcp))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("droits et taxes de douane ne sont pas renseignés"));
    }

    // ── les cas sans crédit extérieur ──────────────────────────────────────────────────────────

    @Test
    @Transactional
    void sansCreditExterieur_aucuneVentilationExigee() {
        CertificatCredit cert = certificatNeuf();

        // Un marché purement intérieur n'a aucune ligne douanière à ventiler.
        UpdateCertificatCreditMontantsRequest req = UpdateCertificatCreditMontantsRequest.builder()
                .montantCordon(BigDecimal.ZERO)
                .montantTVAInterieure(BigDecimal.ZERO)
                .build();

        CertificatCreditDto dto = service.updateMontants(cert.getId(), req, dgtcp);

        assertThat(dto.getMontantCordon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private CertificatCredit certificatNeuf() {
        CertificatCredit modele = certificatRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun certificat amorcé"));
        CertificatCredit cert = new CertificatCredit();
        cert.setNumero("CI-VENT-" + System.nanoTime());
        cert.setReference("CR-VENT-" + System.nanoTime());
        cert.setEntreprise(modele.getEntreprise());
        return certificatRepository.save(cert);
    }

    private UpdateCertificatCreditMontantsRequest ventilation(BigDecimal droits, BigDecimal tvaImport,
                                                              BigDecimal taxesConsommation) {
        return UpdateCertificatCreditMontantsRequest.builder()
                .montantCordon(CREDIT_EXTERIEUR)
                .montantTVAInterieure(BigDecimal.ZERO)
                .droitsEtTaxesDouaneHorsTva(droits)
                .tvaImportationDouane(tvaImport)
                .taxesConsommation(taxesConsommation)
                .build();
    }
}
