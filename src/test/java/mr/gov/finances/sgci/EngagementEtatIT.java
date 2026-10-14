package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AxeEngagement;
import mr.gov.finances.sgci.domain.enums.FiabiliteAgregat;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.EngagementEtatService;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementLigneDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le consommé ne compte jamais deux fois, et ne se trompe pas sur un transfert.
 *
 * <p>{@link #leConsommeNeCompteJamaisLaTvaImportDeuxFois()} est le test central : il fige le fait
 * qui rend la formule intuitive fausse. {@code applyOuvertureInitialisation} ouvre
 * {@code soldeCordon} aux seuls droits et taxes hors TVA (b + c), alors que l'invariant du
 * récapitulatif impose {@code montantCordon = b + c + d}. Un {@code montantCordon − soldeCordon}
 * affiche donc d comme consommé <em>sur un certificat ouvert et jamais utilisé</em>.
 *
 * <p>{@link #unTransfertNeCreePasDeConsommationFictive()} fige le second : le transfert vide le
 * quota de TVA à l'import dans le stock déductible, ce qui n'est pas une consommation mais une
 * reclassification.
 *
 * <p>Chaque test travaille sur <b>sa propre entreprise</b> et interroge l'axe entreprise en filtrant
 * dessus : les chiffres mesurés ne contiennent alors rien du jeu de démonstration, et le test ne
 * dépend ni de son contenu ni de son évolution.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class EngagementEtatIT {

    private static final Instant DEBUT = Instant.now().minus(30, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private EngagementEtatService service;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private TvaDeductibleStockRepository stockRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire engagement " + System.nanoTime())
                .nif("NIF-ENG-" + System.nanoTime())
                .build());
    }

    // ── le fait central ────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leConsommeNeCompteJamaisLaTvaImportDeuxFois() {
        // b = 100, c = 0, d = 50 → montantCordon = 150 ; h = 200. Ouvert, jamais utilisé.
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(200));

        EngagementLigneDto ligne = ligneDuTitulaire();

        assertThat(ligne.getAccordeTotal()).isEqualByComparingTo(BigDecimal.valueOf(350));
        assertThat(ligne.getRestantTotal()).isEqualByComparingTo(BigDecimal.valueOf(350));
        // La formule naïve montantCordon − soldeCordon vaudrait 50 ici.
        assertThat(ligne.getConsommeSoldes()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getConsommeCordonHorsTva()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getConsommeTvaImport()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getFiabilite()).isEqualTo(FiabiliteAgregat.CONFORME);
    }

    @Test
    @Transactional
    void unTransfertNeCreePasDeConsommationFictive() {
        // Le transfert met le quota de TVA à l'import à zéro et déverse le reliquat dans le stock.
        CertificatCredit c = certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(200));
        stockRepository.save(TvaDeductibleStock.builder()
                .certificatCredit(c)
                .source(TvaDeductibleStockSource.TRANSFERT_CREDIT)
                .montantInitial(BigDecimal.valueOf(50))
                .montantRestant(BigDecimal.valueOf(50))
                .dateCreation(Instant.now())
                .build());

        EngagementLigneDto ligne = ligneDuTitulaire();

        // Sans la réintégration du stock, la TVA à l'import apparaîtrait consommée à 100 %.
        assertThat(ligne.getRestantTvaImport()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(ligne.getConsommeTvaImport()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getConsommeSoldes()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @Transactional
    void uneLiquidationDouaneDebiteLeHorsTvaEtReclasseLaTva() {
        // Après une liquidation de 60 dont 20 de TVA : cordon 100 → 60, quota d 50 → 30, stock 20.
        CertificatCredit c = certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(60), BigDecimal.valueOf(30), BigDecimal.valueOf(200));
        stockRepository.save(TvaDeductibleStock.builder()
                .certificatCredit(c)
                .source(TvaDeductibleStockSource.UTILISATION_DOUANE)
                .montantInitial(BigDecimal.valueOf(20))
                .montantRestant(BigDecimal.valueOf(20))
                .dateCreation(Instant.now())
                .build());

        EngagementLigneDto ligne = ligneDuTitulaire();

        // Seule la part hors TVA est consommée : 40. La TVA n'est que reclassée.
        assertThat(ligne.getConsommeCordonHorsTva()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(ligne.getRestantTvaImport()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(ligne.getConsommeTvaImport()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getConsommeSoldes()).isEqualByComparingTo(BigDecimal.valueOf(40));
        // Aucun mouvement n'existe en base pour ce dossier : l'écart doit être publié, pas masqué.
        assertThat(ligne.getConsommeMouvements()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ligne.getEcartMouvements()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(ligne.getFiabilite()).isEqualTo(FiabiliteAgregat.A_VERIFIER);
    }

    // ── ce qu'il ne faut pas rogner ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unReportANouveauNestPasEcreteAZero() {
        // Le report à nouveau peut porter un solde au-dessus de son enveloppe. C'est légitime.
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(260));

        EngagementEtatDto dto = engagement();
        EngagementLigneDto ligne = ligneDe(dto);

        assertThat(dto.getNbRestantSuperieurAccorde()).isPositive();
        // Le consommé devient négatif, et il n'est pas ramené à zéro.
        assertThat(ligne.getConsommeTvaInterieure()).isEqualByComparingTo(BigDecimal.valueOf(-60));
    }

    @Test
    @Transactional
    void laVentilationTotaleEstConservee() {
        // Σ accordé doit égaler montantCordon + montantTVAInterieure, grâce à l'invariant b + c + d.
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(200));

        EngagementLigneDto ligne = ligneDuTitulaire();

        assertThat(ligne.getAccordeTotal())
                .isEqualByComparingTo(BigDecimal.valueOf(150).add(BigDecimal.valueOf(200)));
    }

    @Test
    @Transactional
    void unCertificatSansDemandeTombeDansHorsRattachement() {
        // Ces certificats existent — la clé étrangère est nullable — et doivent apparaître comme une
        // ligne nommée. Les masquer donnerait un tableau dont les lignes ne font pas le total.
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(50), BigDecimal.valueOf(200),
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(200));

        EngagementEtatDto parBailleur = service.parAxe(
                president, DEBUT, FIN, null, titulaire.getId(), AxeEngagement.BAILLEUR);

        assertThat(parBailleur.getLignes())
                .extracting(EngagementLigneDto::getCle)
                .contains("HORS_RATTACHEMENT");
        assertThat(parBailleur.getTotal().getAccordeTotal())
                .isEqualByComparingTo(BigDecimal.valueOf(350));
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private EngagementEtatDto engagement() {
        return service.parAxe(president, DEBUT, FIN, null, titulaire.getId(), AxeEngagement.ENTREPRISE);
    }

    private EngagementLigneDto ligneDuTitulaire() {
        return ligneDe(engagement());
    }

    private EngagementLigneDto ligneDe(EngagementEtatDto dto) {
        return dto.getLignes().stream()
                .filter(l -> String.valueOf(titulaire.getId()).equals(l.getCle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Aucune ligne pour l'entreprise du test ; lignes = " + dto.getLignes()));
    }

    private CertificatCredit certificat(BigDecimal montantCordon, BigDecimal droitsB, BigDecimal taxesC,
                                        BigDecimal tvaImportAccordeeD, BigDecimal montantTvaInterieure,
                                        BigDecimal soldeCordon, BigDecimal quotaTvaImport,
                                        BigDecimal soldeTva) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-ENG-" + System.nanoTime())
                .dateEmission(Instant.now())
                .statut(StatutCertificat.OUVERT)
                .entreprise(titulaire)
                .montantCordon(montantCordon)
                .droitsEtTaxesDouaneHorsTva(droitsB)
                .taxesConsommation(taxesC)
                .tvaImportationDouaneAccordee(tvaImportAccordeeD)
                .montantTVAInterieure(montantTvaInterieure)
                .soldeCordon(soldeCordon)
                .tvaImportationDouane(quotaTvaImport)
                .soldeTVA(soldeTva)
                .build());
    }
}
