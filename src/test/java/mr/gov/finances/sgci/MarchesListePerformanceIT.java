package mr.gov.finances.sgci;

import jakarta.persistence.EntityManagerFactory;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.MarcheDto;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La liste « Attributions / Marchés » restait bloquée en production : chaque marché chargeait sa
 * demande de correction, et celle-ci ses objets liés (modèle fiscal, DQE, feuille d'évaluation),
 * une requête chacun. Le nombre de requêtes ne doit plus croître avec le nombre de marchés.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarchesListePerformanceIT {

    @Autowired private MarcheService service;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EntityManagerFactory emf;

    @Test
    void laListeDesMarchesNeFaitPasUneRequeteParMarche() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        AuthenticatedUser president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);

        long avant = requetes(() -> service.findAll(president));
        creerMarchesAvecDemande(60);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        List<MarcheDto> liste = service.findAll(president);
        long apres = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);

        assertThat(liste).hasSizeGreaterThanOrEqualTo(60);
        assertThat(liste).filteredOn(m -> m.getDemandeCorrectionId() != null).hasSizeGreaterThanOrEqualTo(60);
        System.out.printf("Requêtes pour la liste des marchés : %d avant, %d après 60 marchés de plus%n", avant, apres);
        // Quelques requêtes par lot, pas une (ou plusieurs) par marché.
        assertThat(apres - avant).as("requêtes supplémentaires pour 60 marchés").isLessThan(20);
    }

    @Autowired private mr.gov.finances.sgci.service.DemandeCorrectionService demandeService;

    /** La page Marchés charge aussi la liste des demandes de correction. */
    @Test
    void laListeDesDemandesNeFaitPasUneRequeteParDemande() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        AuthenticatedUser president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);

        long avant = requetes(() -> demandeService.findAll(president));
        creerMarchesAvecDemande(30);
        long apres = requetes(() -> demandeService.findAll(president));

        System.out.printf("Requêtes pour la liste des demandes : %d avant, %d après 30 demandes de plus%n", avant, apres);
        assertThat(apres - avant).as("requêtes supplémentaires pour 30 demandes").isLessThan(20);
    }

    private long requetes(Runnable r) {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        r.run();
        long n = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);
        return n;
    }

    private void creerMarchesAvecDemande(int nombre) {
        DemandeCorrection modele = demandeRepository.findAll().stream().findFirst().orElseThrow();
        String n = String.valueOf(System.nanoTime());
        for (int i = 0; i < nombre; i++) {
            DemandeCorrection d = demandeRepository.save(DemandeCorrection.builder()
                    .numero("DC-PERF-" + n + "-" + i)
                    .statut(StatutDemande.NOTIFIEE)
                    .dateDepot(Instant.now())
                    .autoriteContractante(modele.getAutoriteContractante())
                    .convention(modele.getConvention())
                    .entreprise(modele.getEntreprise())
                    .build());
            marcheRepository.save(Marche.builder()
                    .numeroMarche("PERF-" + n + "-" + i)
                    .intitule("Marché de mesure " + i)
                    .montantContratHt(BigDecimal.TEN)
                    .statut(StatutMarche.EN_COURS)
                    .convention(modele.getConvention())
                    .autoriteContractante(modele.getAutoriteContractante())
                    .demandeCorrection(d)
                    .build());
        }
    }
}
