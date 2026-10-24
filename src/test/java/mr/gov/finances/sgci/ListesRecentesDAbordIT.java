package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La dernière convention et le dernier marché ajoutés apparaissent en tête de liste.
 *
 * <p>Le serveur renvoyait les deux listes dans l'ordre de la base, du plus ancien au plus récent :
 * un ajout se retrouvait en bas de page. Chaque test crée deux éléments l'un après l'autre et
 * vérifie que le second précède le premier, pour chaque chemin de lecture — ils passent par des
 * requêtes différentes selon le rôle et selon qu'il y a une recherche.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ListesRecentesDAbordIT {

    @Autowired private ConventionService conventionService;
    @Autowired private MarcheService marcheService;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser ac;
    private AuthenticatedUser dgb;
    private AutoriteContractante mienne;

    @BeforeEach
    void setUp() {
        Utilisateur agent = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(agent.getId(), agent.getUsername(), Role.AUTORITE_CONTRACTANTE);
        mienne = agent.getAutoriteContractante();
        Utilisateur direction = utilisateurRepository.findByUsername("dgb").orElseThrow();
        dgb = new AuthenticatedUser(direction.getId(), direction.getUsername(), Role.DGB);
    }

    // ── conventions ────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laDerniereConventionAjouteeEstEnTeteDeLaListeDeLAutorite() {
        Convention ancienne = convention("TRI-A");
        Convention recente = convention("TRI-B");

        assertPremiere(conventionService.findAll(ac, null), recente, ancienne);
    }

    @Test
    @Transactional
    void lOrdreTientAussiPourUneDirection() {
        // Une direction lit toutes les conventions, par une autre requête que l'autorité.
        Convention ancienne = convention("TRI-C");
        Convention recente = convention("TRI-D");

        assertPremiere(conventionService.findAll(dgb, null), recente, ancienne);
    }

    @Test
    @Transactional
    void lOrdreTientPendantUneRechercheEtParStatut() {
        String motCle = "TRITRI" + System.nanoTime();
        Convention ancienne = convention(motCle + "-1");
        Convention recente = convention(motCle + "-2");

        assertPremiere(conventionService.findAll(ac, motCle), recente, ancienne);
        assertPremiere(conventionService.findByStatut(StatutConvention.EN_ATTENTE, ac), recente, ancienne);
    }

    @Test
    @Transactional
    void uneConventionSansBailleurSeTrouveParLaRecherche() {
        // La condition sur le nom du bailleur imposait une jointure interne : sans bailleur, la
        // convention disparaissait des résultats, même avec une référence identique.
        String motCle = "SANSBAILLEUR" + System.nanoTime();
        Convention sansBailleur = convention(motCle);

        assertThat(conventionService.findAll(ac, motCle)).extracting(ConventionDto::getId)
                .containsExactly(sansBailleur.getId());
    }

    // ── marchés ────────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leDernierMarcheAjouteEstEnTeteDeLaListe() {
        Convention convention = convention("TRI-MARCHES");
        Marche ancien = marche(convention, "M-TRI-A");
        Marche recent = marche(convention, "M-TRI-B");

        assertPremier(marcheService.findAll(ac), recent, ancien);
        assertPremier(marcheService.findAll(dgb), recent, ancien);
    }

    @Test
    @Transactional
    void lOrdreDesMarchesTientPendantUneRecherche() {
        String motCle = "MTRI" + System.nanoTime();
        Convention convention = convention("TRI-RECHERCHE");
        Marche ancien = marche(convention, motCle + "-1");
        Marche recent = marche(convention, motCle + "-2");

        assertPremier(marcheService.searchMarches(motCle, ac), recent, ancien);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private void assertPremiere(List<ConventionDto> liste, Convention recente, Convention ancienne) {
        List<Long> ids = liste.stream().map(ConventionDto::getId).toList();
        assertThat(ids).contains(recente.getId(), ancienne.getId());
        assertThat(ids.indexOf(recente.getId()))
                .as("la convention la plus récente doit précéder l'ancienne")
                .isLessThan(ids.indexOf(ancienne.getId()));
    }

    private void assertPremier(List<MarcheDto> liste, Marche recent, Marche ancien) {
        List<Long> ids = liste.stream().map(MarcheDto::getId).toList();
        assertThat(ids).contains(recent.getId(), ancien.getId());
        assertThat(ids.indexOf(recent.getId()))
                .as("le marché le plus récent doit précéder l'ancien")
                .isLessThan(ids.indexOf(ancien.getId()));
    }

    private Convention convention(String reference) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference(reference + "-" + System.nanoTime())
                .intitule("Convention " + reference)
                .statut(StatutConvention.EN_ATTENTE)
                .autoriteContractante(mienne)
                .creeParAutoriteContractante(mienne)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
    }

    private Marche marche(Convention convention, String numero) {
        return marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche(numero)
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(100_000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build());
    }
}
