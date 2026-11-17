package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.web.dto.reporting.NamedCountDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingDemandeStatsDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le compteur « Demandes » du reporting : toutes les demandes déposées, tous processus confondus,
 * hors brouillons et hors reprise d'archive, avec le détail par processus (remarque 2 de la recette).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportingDemandesParProcessusIT {

    private static final Instant DEBUT = Instant.now().minus(7300, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired private ReportingService service;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    @Transactional
    void leTotalEstLaSommeDesProcessusSansBrouillonNiArchive() {
        ReportingDemandeStatsDto stats = national();

        assertThat(stats.getParProcessus()).extracting(NamedCountDto::getKey).containsExactly(
                "CORRECTION", "MISE_EN_PLACE", "UTILISATION_DOUANE", "UTILISATION_TVA_INTERIEURE",
                "TRANSFERT", "AVENANT", "SOUS_TRAITANCE");
        assertThat(stats.getTotal()).isEqualTo(stats.getParProcessus().stream().mapToLong(NamedCountDto::getCount).sum());
        assertThat(stats.getByStatut()).extracting(NamedCountDto::getKey).doesNotContain(StatutDemande.BROUILLON.name());
        assertThat(parProcessus(stats).get("CORRECTION"))
                .isEqualTo(stats.getByStatut().stream().mapToLong(NamedCountDto::getCount).sum());
    }

    @Test
    @Transactional
    void unBrouillonNEstPasUneDemandeDeposee() {
        ReportingDemandeStatsDto avant = national();
        DemandeCorrection d = demandeRepository.findAll().stream()
                .filter(x -> x.getStatut() != StatutDemande.BROUILLON)
                .filter(x -> x.getNumero() == null || !x.getNumero().startsWith("ARCHIVE-"))
                .filter(x -> {
                    Instant date = x.getDateDepot() != null ? x.getDateDepot() : x.getDateCreation();
                    return date != null && date.isAfter(DEBUT) && date.isBefore(FIN);
                })
                .findFirst().orElseThrow();
        d.setStatut(StatutDemande.BROUILLON);
        demandeRepository.saveAndFlush(d);

        ReportingDemandeStatsDto apres = national();

        assertThat(apres.getTotal()).isEqualTo(avant.getTotal() - 1);
        assertThat(parProcessus(apres).get("CORRECTION")).isEqualTo(parProcessus(avant).get("CORRECTION") - 1);
    }

    private ReportingDemandeStatsDto national() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        return service.getSummary(new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole()),
                DEBUT, FIN, null, null).getDemandes();
    }

    private static Map<String, Long> parProcessus(ReportingDemandeStatsDto stats) {
        return stats.getParProcessus().stream().collect(Collectors.toMap(NamedCountDto::getKey, NamedCountDto::getCount));
    }
}
