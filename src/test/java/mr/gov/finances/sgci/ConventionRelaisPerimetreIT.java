package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un agent de la Commission agissant pour une autorité contractante ne voit que ses conventions.
 *
 * <p>Défaut rapporté par l'équipe, et confirmé sur <b>deux</b> chemins : {@code resolveConventionList}
 * et {@code canAccessConvention} lisaient tous deux {@code Utilisateur.getRole()} en base. Or un
 * relais en impersonation garde le rôle {@code COMMISSION_RELAIS} sur sa ligne — seul le jeton porte
 * le rôle de l'autorité qu'il assiste. Il ne tombait donc pas dans la branche des autorités et
 * repartait avec <b>la totalité des conventions</b>, liste comme accès direct par identifiant.
 *
 * <p>C'est la même cause racine que le défaut d'habilitation du reporting : l'identité effective
 * ignorée au profit de la ligne en base. D'où les deux assertions ci-dessous, et non une seule —
 * restreindre la liste sans fermer l'accès par identifiant rouvrirait tout ce que la liste masque.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConventionRelaisPerimetreIT {

    @Autowired
    private ConventionService service;

    @Autowired
    private ConventionRepository conventionRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private Utilisateur relais;
    private AutoriteContractante autoriteAssistee;
    private AutoriteContractante autoriteTierce;
    private Convention conventionAssistee;
    private Convention conventionTierce;

    @BeforeEach
    void setUp() {
        relais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        // Le relais n'a aucun rattachement propre : c'est toute la raison de l'impersonation.
        assertThat(relais.getAutoriteContractante()).isNull();

        autoriteAssistee = autoriteRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune autorité contractante amorcée"));
        autoriteTierce = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité tierce " + System.nanoTime())
                .code("AC-TIERCE-" + System.nanoTime())
                .build());

        conventionAssistee = convention(autoriteAssistee, "CONV-ASSISTEE-");
        conventionTierce = convention(autoriteTierce, "CONV-TIERCE-");
    }

    @Test
    @Transactional
    void leRelaisImpersonantNeVoitQueLesConventionsDeSonAutorite() {
        List<ConventionDto> vues = service.findAll(impersonant(), null);

        assertThat(vues).extracting(ConventionDto::getId)
                .contains(conventionAssistee.getId())
                .doesNotContain(conventionTierce.getId());
    }

    @Test
    @Transactional
    void leRelaisImpersonantNOuvrePasLaConventionDUneAutreAutorite() {
        // Sans ce contrôle, restreindre la liste n'aurait aucune valeur : l'accès direct par
        // identifiant rouvrirait tout ce que la liste vient de masquer.
        AuthenticatedUser acteur = impersonant();

        assertThatThrownBy(() -> service.findById(conventionTierce.getId(), acteur))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
        assertThat(service.findById(conventionAssistee.getId(), acteur).getId())
                .isEqualTo(conventionAssistee.getId());
    }

    @Test
    @Transactional
    void leRelaisNonImpersonantGardeSonPerimetreDeSupport() {
        // Hors impersonation, le relais n'agit pour personne : son rôle ne tombe dans aucune
        // branche d'autorité, et c'est le comportement de support attendu — ce test existe pour
        // que la correction ne le casse pas en durcissant à la hache.
        AuthenticatedUser sansImpersonation =
                new AuthenticatedUser(relais.getId(), relais.getUsername(), Role.COMMISSION_RELAIS);

        assertThat(service.findAll(sansImpersonation, null))
                .extracting(ConventionDto::getId)
                .contains(conventionAssistee.getId(), conventionTierce.getId());
    }

    @Test
    @Transactional
    void uneAutoriteOrdinaireResteCadreeCommeAvant() {
        // La correction passe par le rôle effectif : une autorité qui se connecte normalement doit
        // continuer de ne voir que son périmètre, sans impersonation dans l'équation.
        Utilisateur agentAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        AuthenticatedUser acteur = new AuthenticatedUser(
                agentAc.getId(), agentAc.getUsername(), Role.AUTORITE_CONTRACTANTE);

        assertThat(service.findAll(acteur, null))
                .extracting(ConventionDto::getId)
                .doesNotContain(conventionTierce.getId());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    /** Le jeton délivré par l'impersonation : rôle de la cible, identifiant du relais. */
    private AuthenticatedUser impersonant() {
        return new AuthenticatedUser(relais.getId(), relais.getUsername(),
                Role.AUTORITE_CONTRACTANTE, true, null, autoriteAssistee.getId());
    }

    private Convention convention(AutoriteContractante autorite, String prefixe) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference(prefixe + System.nanoTime())
                .intitule("Convention de périmètre")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(autorite)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
    }
}
