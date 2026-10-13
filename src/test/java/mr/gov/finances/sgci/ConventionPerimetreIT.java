package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.convention.ConventionDisponibilitePolicy;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une autorité contractante ne voit que son périmètre, et une convention fermée n'accueille plus rien.
 *
 * <p>Les conventions étaient jusqu'ici un référentiel partagé — chaque AC recevait {@code findAll()}.
 * La recette a renversé cette décision. Le test qui compte est
 * {@link #accesDirect_horsPerimetre_refuse()} : restreindre la liste sans restreindre l'accès par
 * identifiant n'aurait masqué que l'affichage.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConventionPerimetreIT {

    @Autowired
    private ConventionService service;

    @Autowired
    private MarcheService marcheService;

    @Autowired
    private ConventionRepository conventionRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser ac;
    private AuthenticatedUser dgtcp;
    private AutoriteContractante mienne;

    @BeforeEach
    void setUp() {
        Utilisateur agentAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(agentAc.getId(), agentAc.getUsername(), agentAc.getRole());
        mienne = agentAc.getAutoriteContractante();
        Utilisateur agentDgtcp = utilisateurRepository.findByUsername("dgtcp").orElseThrow();
        dgtcp = new AuthenticatedUser(agentDgtcp.getId(), agentDgtcp.getUsername(), agentDgtcp.getRole());
    }

    // ── le périmètre : créatrice OU titulaire ──────────────────────────────────────────────────

    @Test
    @Transactional
    void conventionDontElleEstTitulaire_visible() {
        Convention c = convention(mienne, mienne, true);

        assertThat(references(service.findAll(ac, null))).contains(c.getReference());
    }

    @Test
    @Transactional
    void conventionQuelleACreeePourUneAutre_visible() {
        // 210 des 215 conventions en base n'ont aucune créatrice : filtrer sur la seule créatrice
        // aurait vidé la liste. Le périmètre retient donc les deux liens.
        Convention c = convention(autreAutorite(), mienne, true);

        assertThat(references(service.findAll(ac, null))).contains(c.getReference());
    }

    @Test
    @Transactional
    void conventionDuneAutreAutorite_invisible() {
        AutoriteContractante autre = autreAutorite();
        Convention c = convention(autre, autre, true);

        assertThat(references(service.findAll(ac, null))).doesNotContain(c.getReference());
    }

    @Test
    @Transactional
    void accesDirect_horsPerimetre_refuse() {
        AutoriteContractante autre = autreAutorite();
        Convention c = convention(autre, autre, true);

        assertThatThrownBy(() -> service.findById(c.getId(), ac))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    // ── la désactivation : bloque et masque ────────────────────────────────────────────────────

    @Test
    @Transactional
    void conventionDesactivee_masqueeAlAutorite() {
        Convention c = convention(mienne, mienne, false);

        assertThat(references(service.findAll(ac, null))).doesNotContain(c.getReference());
    }

    @Test
    @Transactional
    void conventionDesactivee_resteVisibleALaCommission() {
        // Sans cela, personne ne pourrait plus la rouvrir.
        Convention c = convention(mienne, mienne, false);

        assertThat(references(service.findAll(dgtcp, null))).contains(c.getReference());
    }

    @Test
    @Transactional
    void conventionDesactivee_refuseUnNouveauMarche() {
        Convention c = convention(mienne, mienne, false);

        CreateMarcheRequest req = CreateMarcheRequest.builder()
                .conventionId(c.getId())
                .numeroMarche("M-DESACT-" + System.nanoTime())
                .intitule("Marché sur convention fermée")
                .montantContratHt(BigDecimal.valueOf(500_000))
                .statut(StatutMarche.EN_COURS)
                .build();

        assertThatThrownBy(() -> marcheService.create(req, ac))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .isEqualTo(ConventionDisponibilitePolicy.MOTIF_DESACTIVEE));
    }

    @Test
    @Transactional
    void reactivation_rendLaConventionDeNouveauVisible() {
        Convention c = convention(mienne, mienne, false);

        service.changerActivation(c.getId(), true, 1L);

        assertThat(references(service.findAll(ac, null))).contains(c.getReference());
    }

    // ── la règle de lecture de la colonne ──────────────────────────────────────────────────────

    @Test
    void activationNonRenseignee_vautActive() {
        // Les 215 conventions antérieures ont la colonne à null : la lire comme « désactivée » les
        // aurait toutes fermées d'un coup.
        Convention ancienne = new Convention();
        ancienne.setActif(null);

        assertThat(ConventionDisponibilitePolicy.estActive(ancienne)).isTrue();
    }

    @Test
    void seulsLesAgentsDeLaCommissionVoientLesFermees() {
        for (Role role : List.of(Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT, Role.ADMIN_SI)) {
            assertThat(ConventionDisponibilitePolicy.voitLesDesactivees(role)).as("rôle %s", role).isTrue();
        }
        for (Role role : List.of(Role.AUTORITE_CONTRACTANTE, Role.AUTORITE_UPM, Role.AUTORITE_UEP,
                Role.ENTREPRISE, Role.SOUS_TRAITANT)) {
            assertThat(ConventionDisponibilitePolicy.voitLesDesactivees(role)).as("rôle %s", role).isFalse();
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private List<String> references(List<ConventionDto> conventions) {
        return conventions.stream().map(ConventionDto::getReference).toList();
    }

    private AutoriteContractante autreAutorite() {
        return autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité tierce " + System.nanoTime())
                .code("AC-" + System.nanoTime())
                .build());
    }

    private Convention convention(AutoriteContractante titulaire, AutoriteContractante creatrice, boolean actif) {
        return conventionRepository.save(Convention.builder()
                .reference("CONV-PERIM-" + System.nanoTime())
                .intitule("Convention de périmètre")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(titulaire)
                .creeParAutoriteContractante(creatrice)
                .actif(actif)
                .dateCreation(Instant.now())
                .build());
    }
}
