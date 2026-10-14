package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.web.dto.reporting.NamedCountDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingSummaryDto;
import mr.gov.finances.sgci.web.dto.reporting.TimeSeriesPointDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les chiffres du reporting ne sortent jamais du périmètre du rôle.
 *
 * <p>Le test qui compte est {@link #leSummaireDUneEntrepriseNeDepassePasLeNational()}, et ce qui en
 * fait un test est l'inégalité <strong>stricte</strong> exigée sur au moins une ligne : une
 * régression qui remplacerait le filtre par {@code where 1=1} conserverait l'inégalité large, et
 * passerait inaperçue. C'est pourquoi le test commence par déplacer un dossier vers une seconde
 * entreprise — sans cela, l'inégalité stricte serait impossible à obtenir sur le jeu de
 * démonstration, où tout appartient au même titulaire.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste. L'habilitation des routes est figée
 * séparément par {@code ReportingHabilitationIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportingPerimetreIT {

    /** Large, pour que le test ne dépende pas des dates du jeu de démonstration. */
    private static final Instant DEBUT = Instant.now().minus(7300, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private ReportingService service;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    @Transactional
    void leSummaireDUneEntrepriseNeDepassePasLeNational() {
        Entreprise seconde = entrepriseDe("test");
        Entreprise premiere = entrepriseDe("entreprise");
        assertThat(seconde.getId()).isNotEqualTo(premiere.getId());
        deplacerUnDossierVers(seconde, premiere);

        ReportingSummaryDto national = summaire("president", Role.PRESIDENT);
        ReportingSummaryDto vuA = summaire("entreprise", Role.ENTREPRISE);
        ReportingSummaryDto vuB = summaire("test", Role.ENTREPRISE);

        // Inégalité large partout…
        assertThat(vuA.getDemandes().getTotal()).isLessThanOrEqualTo(national.getDemandes().getTotal());
        assertThat(vuA.getCertificatsTotal()).isLessThanOrEqualTo(national.getCertificatsTotal());
        assertThat(vuA.getUtilisationsTotal()).isLessThanOrEqualTo(national.getUtilisationsTotal());
        Map<String, Long> parStatutNational = parStatut(national.getDemandes().getByStatut());
        parStatut(vuA.getDemandes().getByStatut()).forEach((statut, compte) ->
                assertThat(compte)
                        .as("statut %s hors du périmètre de l'entreprise", statut)
                        .isLessThanOrEqualTo(parStatutNational.getOrDefault(statut, 0L)));

        // …et stricte sur le total, puisque la seconde entreprise détient maintenant un dossier.
        assertThat(vuB.getDemandes().getTotal()).isPositive();
        assertThat(vuA.getDemandes().getTotal()).isLessThan(national.getDemandes().getTotal());
        assertThat(vuA.getDemandes().getTotal() + vuB.getDemandes().getTotal())
                .isLessThanOrEqualTo(national.getDemandes().getTotal());
    }

    @Test
    @Transactional
    void laSerieTemporelleEstCadreeCommeLeSummaire() {
        // La série est le seul endroit qui n'emploie pas une clause complète mais seulement les
        // conjoints de périmètre : elle a sa propre occasion d'oublier le filtre.
        Entreprise seconde = entrepriseDe("test");
        deplacerUnDossierVers(seconde, entrepriseDe("entreprise"));

        long national = totalSerie("president", Role.PRESIDENT);
        long vuA = totalSerie("entreprise", Role.ENTREPRISE);
        long vuB = totalSerie("test", Role.ENTREPRISE);

        assertThat(vuB).isPositive();
        assertThat(vuA).isLessThan(national);
        assertThat(vuA + vuB).isLessThanOrEqualTo(national);
    }

    @Test
    @Transactional
    void lUpmSansMarcheDelegueNeVoitRien() {
        // Un délégué ne voit que les marchés qui lui sont délégués, jamais tout le portefeuille de
        // son autorité. Le prédicat est un « exists » à trois jointures répliqué dans cinq clauses :
        // c'est la ligne la plus facile à oublier dans une sixième.
        Utilisateur agentAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        assertThat(agentAc.getAutoriteContractante()).isNotNull();
        Utilisateur upm = utilisateurRepository.save(Utilisateur.builder()
                .username("upm-sans-delegation-" + System.nanoTime())
                .passwordHash("x")
                .role(Role.AUTORITE_UPM)
                .autoriteContractante(agentAc.getAutoriteContractante())
                .nomComplet("UPM sans délégation")
                .email("upm-" + System.nanoTime() + "@sharklasers.com")
                .actif(true)
                .build());

        ReportingSummaryDto vuAc = summaire("ac", Role.AUTORITE_CONTRACTANTE);
        ReportingSummaryDto vuUpm = service.getSummary(
                new AuthenticatedUser(upm.getId(), upm.getUsername(), Role.AUTORITE_UPM),
                DEBUT, FIN, null, null);

        assertThat(vuAc.getDemandes().getTotal()).isPositive();
        assertThat(vuUpm.getDemandes().getTotal()).isZero();
    }

    @Test
    @Transactional
    void lesAgregatsFinanciersSuiventLePerimetre() {
        // Le commentaire du DTO annonçait un filtrage par rôle qui n'existait pas. Le vrai
        // mécanisme est le périmètre : ces sommes ne portent que sur les certificats visibles.
        ReportingSummaryDto national = summaire("president", Role.PRESIDENT);
        ReportingSummaryDto vuA = summaire("entreprise", Role.ENTREPRISE);

        assertThat(vuA.getCertificatFinancials()).isNotNull();
        assertThat(vuA.getCertificatFinancials().getCertificatCount())
                .isLessThanOrEqualTo(national.getCertificatFinancials().getCertificatCount());
        assertThat(vuA.getCertificatFinancials().getSumMontantCordon())
                .isLessThanOrEqualTo(national.getCertificatFinancials().getSumMontantCordon());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private ReportingSummaryDto summaire(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return service.getSummary(
                new AuthenticatedUser(u.getId(), u.getUsername(), role), DEBUT, FIN, null, null);
    }

    private long totalSerie(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        List<TimeSeriesPointDto> serie = service.getDemandeTimeseries(
                new AuthenticatedUser(u.getId(), u.getUsername(), role), DEBUT, FIN, null, null);
        return serie.stream().mapToLong(TimeSeriesPointDto::getCount).sum();
    }

    private Entreprise entrepriseDe(String username) {
        Entreprise e = utilisateurRepository.findByUsername(username).orElseThrow().getEntreprise();
        assertThat(e).as("le compte %s doit porter une entreprise", username).isNotNull();
        return e;
    }

    /** Déplace un dossier de {@code source} vers {@code cible}, pour créer l'écart à mesurer. */
    private void deplacerUnDossierVers(Entreprise cible, Entreprise source) {
        DemandeCorrection dossier = demandeRepository.findAll().stream()
                .filter(d -> d.getEntreprise() != null
                        && source.getId().equals(d.getEntreprise().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune demande rattachée à l'entreprise de démonstration"));
        dossier.setEntreprise(cible);
        // La fenêtre du test est large, mais la demande doit tout de même y tomber.
        if (dossier.getDateDepot() == null && dossier.getDateCreation() == null) {
            dossier.setDateDepot(Instant.now());
        }
        demandeRepository.saveAndFlush(dossier);
    }

    private Map<String, Long> parStatut(List<NamedCountDto> lignes) {
        return lignes.stream().collect(Collectors.toMap(
                NamedCountDto::getKey, NamedCountDto::getCount, Long::sum));
    }
}
