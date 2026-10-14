package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.CalendrierPeriodes;
import mr.gov.finances.sgci.service.reporting.ProductionPeriodeService;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPeriodeDto;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La production par période, branchée sur la base.
 *
 * <p>{@link #laMasseAccordeeEstDateeDeLaMiseEnPlace()} est le test qui compte : la masse ne peut
 * pas être datée de la création du certificat, qui n'existe pas comme colonne, et la clé retenue
 * — mise en place, à défaut émission — doit être la même que celle du journal des crédits. Si
 * quelqu'un la change pour la date d'émission, deux écrans de l'application se contrediront sans
 * que rien ne tombe.
 *
 * <p>Le découpage du temps lui-même est vérifié sans base par {@code CalendrierPeriodesTest} : ici
 * on ne contrôle que le branchement.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductionPeriodeIT {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private ProductionPeriodeService service;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire production " + System.nanoTime())
                .nif("NIF-PROD-" + System.nanoTime())
                .build());
    }

    @Test
    @Transactional
    void laMasseAccordeeEstDateeDeLaMiseEnPlace() {
        // Émis le mois dernier, mis en place ce mois-ci : la masse doit tomber sur la mise en
        // place, pas sur l'émission. C'est la clé du journal des crédits, et la reprendre est ce
        // qui empêche deux écrans de se contredire.
        LocalDate aujourdhui = LocalDate.now(ZONE);
        LocalDate moisDernier = aujourdhui.minusMonths(1).withDayOfMonth(15);
        certificat(instant(moisDernier), Instant.now(), BigDecimal.valueOf(1_000));

        ProductionPeriodeDto dto = production(Granularite.MOIS);

        String cleMoisCourant = CalendrierPeriodes.cleMois(
                aujourdhui.getYear(), aujourdhui.getMonthValue());
        String cleMoisDernier = CalendrierPeriodes.cleMois(
                moisDernier.getYear(), moisDernier.getMonthValue());

        assertThat(point(dto, cleMoisCourant).getMasseAccordee())
                .isEqualByComparingTo(BigDecimal.valueOf(1_000));
        assertThat(point(dto, cleMoisDernier).getMasseAccordee())
                .isEqualByComparingTo(BigDecimal.ZERO);
        // Le certificat est bien compté au mois de son émission, lui.
        assertThat(point(dto, cleMoisDernier).getNbCertificats()).isEqualTo(1);
    }

    @Test
    @Transactional
    void lesPeriodesVidesSontPresentesAZero() {
        // Une série qui saute les mois sans donnée relie deux mois distants en ligne droite, et
        // l'arrêt qui les sépare disparaît de l'écran.
        ProductionPeriodeDto dto = production(Granularite.MOIS);

        assertThat(dto.getPoints()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(dto.getPoints()).allSatisfy(p -> {
            assertThat(p.getPeriode()).isNotBlank();
            assertThat(p.getMasseAccordee()).isNotNull();
            assertThat(p.getConsommeMouvementsPeriode()).isNotNull();
        });
    }

    @Test
    @Transactional
    void leTrimestreAgregeLesMoisSansLesDemanderALaBase() {
        ProductionPeriodeDto mensuel = production(Granularite.MOIS);
        ProductionPeriodeDto trimestriel = production(Granularite.TRIMESTRE);

        assertThat(trimestriel.getGranularite()).isEqualTo(Granularite.TRIMESTRE);
        assertThat(trimestriel.getPoints()).hasSizeLessThan(mensuel.getPoints().size());
        assertThat(trimestriel.getPoints()).allSatisfy(p ->
                assertThat(p.getPeriode()).matches("\\d{4}-T[1-4]"));
        // Le total ne doit pas dépendre du pas de temps choisi.
        assertThat(trimestriel.getTotalCertificats()).isEqualTo(mensuel.getTotalCertificats());
        assertThat(trimestriel.getTotalMasseAccordee())
                .isEqualByComparingTo(mensuel.getTotalMasseAccordee());
    }

    @Test
    @Transactional
    void laCleDeDatationEstPubliee() {
        // Sans elle, personne ne peut savoir ce que datent les montants, et deux lecteurs en
        // tireront deux conclusions différentes.
        ProductionPeriodeDto dto = production(Granularite.MOIS);

        assertThat(dto.getCleDatation())
                .contains("mise en place")
                .contains("journal des crédits");
    }

    @Test
    @Transactional
    void uneVariationSansReferenceEstNulle() {
        // Une fenêtre antérieure à toute donnée : la variation ne vaut pas -100 %, elle ne vaut
        // rien. C'est à l'écran d'écrire « n/a ».
        Instant debut = instant(LocalDate.of(1990, 1, 1));
        Instant fin = instant(LocalDate.of(1990, 12, 31));

        ProductionPeriodeDto dto = service.production(president, debut, fin, null,
                titulaire.getId(), Granularite.MOIS);

        assertThat(dto.getTotalDemandes()).isZero();
        assertThat(dto.getVariationDemandesPct()).isNull();
        assertThat(dto.getVariationMassePct()).isNull();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private ProductionPeriodeDto production(Granularite granularite) {
        Instant debut = instant(LocalDate.now(ZONE).minusMonths(5).withDayOfMonth(1));
        Instant fin = Instant.now().plusSeconds(3600);
        return service.production(president, debut, fin, null, titulaire.getId(), granularite);
    }

    private ProductionPointDto point(ProductionPeriodeDto dto, String periode) {
        List<ProductionPointDto> trouves = dto.getPoints().stream()
                .filter(p -> periode.equals(p.getPeriode()))
                .toList();
        assertThat(trouves).as("la période %s doit être présente, même vide", periode).hasSize(1);
        return trouves.get(0);
    }

    private CertificatCredit certificat(Instant emission, Instant miseEnPlace, BigDecimal cordon) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-PROD-" + System.nanoTime())
                .dateEmission(emission)
                .dateMiseEnPlace(miseEnPlace)
                .statut(StatutCertificat.OUVERT)
                .entreprise(titulaire)
                .montantCordon(cordon)
                .montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(cordon)
                .soldeTVA(BigDecimal.ZERO)
                .build());
    }

    private static Instant instant(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
