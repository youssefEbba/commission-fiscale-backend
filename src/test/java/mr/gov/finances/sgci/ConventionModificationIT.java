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
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.ConventionController;
import mr.gov.finances.sgci.web.dto.UpdateConventionRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une convention se modifie ou s'annule par son autorité, tant que la DGB ne l'a pas validée.
 *
 * <p>Aucune modification n'existait jusqu'ici, pour personne. Les règles, décidées avec la
 * Commission : en attente de validation seulement ; par l'autorité titulaire ou par celle qui l'a
 * créée pour elle ; tous les champs de la création sauf l'autorité titulaire. L'annulation suit la
 * même règle, par une route à part : celle des directions accepte n'importe quel statut cible, et
 * l'ouvrir à l'autorité lui aurait permis de valider sa propre convention.
 *
 * <p>Les refus comptent autant que les succès : une modification ouverte à tous, ou à tout stade,
 * ferait passer les tests de succès sans rien prouver.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConventionModificationIT {

    @Autowired private ConventionService service;
    @Autowired private ConventionController controller;
    @Autowired private PermissionService permissionService;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser ac;
    private AutoriteContractante mienne;

    @BeforeEach
    void setUp() {
        Utilisateur agent = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = new AuthenticatedUser(agent.getId(), agent.getUsername(), agent.getRole());
        mienne = agent.getAutoriteContractante();
    }

    // ── qui peut modifier ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laTitulaireModifieSaConventionEnAttente() {
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);
        String nouvelleReference = "CONV-CORRIGEE-" + System.nanoTime();

        service.update(c.getId(), modification(nouvelleReference, "Intitulé corrigé"), ac);

        Convention apres = conventionRepository.findById(c.getId()).orElseThrow();
        assertThat(apres.getReference()).isEqualTo(nouvelleReference);
        assertThat(apres.getIntitule()).isEqualTo("Intitulé corrigé");
        assertThat(apres.getMontantMru()).isEqualByComparingTo("2500000");
        assertThat(apres.getDateFin()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThat(apres.getAutoriteContractante().getId())
                .as("l'autorité titulaire ne bouge pas").isEqualTo(mienne.getId());
    }

    @Test
    @Transactional
    void laCreatriceModifieLaConventionQuElleACreePourUneAutre() {
        Convention c = convention(autreAutorite(), mienne, StatutConvention.EN_ATTENTE);

        assertThatCode(() -> service.update(c.getId(), modification(c.getReference(), "Corrigé par la créatrice"), ac))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void uneAutreAutoriteNeModifiePas() {
        AutoriteContractante autre = autreAutorite();
        Convention c = convention(autre, autre, StatutConvention.EN_ATTENTE);

        assertThatThrownBy(() -> service.update(c.getId(), modification(c.getReference(), "Usurpé"), ac))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    @Test
    @Transactional
    void uneDirectionNeModifiePas() {
        // Le Président détient toutes les permissions, la route compris : c'est le service qui
        // réserve la modification à l'autorité.
        for (Role role : List.of(Role.DGB, Role.PRESIDENT, Role.ADMIN_SI)) {
            Utilisateur u = utilisateurRepository.findAll().stream()
                    .filter(x -> x.getRole() == role).findFirst().orElse(null);
            if (u == null) {
                continue;
            }
            Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);
            AuthenticatedUser direction = new AuthenticatedUser(u.getId(), u.getUsername(), role);

            assertThatThrownBy(() -> service.update(c.getId(), modification(c.getReference(), "x"), direction))
                    .as("le rôle %s ne doit pas modifier une convention", role)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
        }
    }

    @Test
    @Transactional
    void leRelaisModifieCommeLAutoriteRepresentee() {
        Utilisateur relais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        AuthenticatedUser enRelais = new AuthenticatedUser(relais.getId(), relais.getUsername(),
                Role.AUTORITE_CONTRACTANTE, true, null, mienne.getId());
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);

        assertThatCode(() -> service.update(c.getId(), modification(c.getReference(), "Corrigé en relais"), enRelais))
                .doesNotThrowAnyException();
    }

    // ── jusqu'à quand ──────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneConventionValideeRejeteeOuAnnuleeNeSeModifiePlus() {
        for (StatutConvention statut : List.of(StatutConvention.VALIDE, StatutConvention.REJETE,
                StatutConvention.ANNULEE)) {
            Convention c = convention(mienne, mienne, statut);

            assertThatThrownBy(() -> service.update(c.getId(), modification(c.getReference(), "Trop tard"), ac))
                    .as("une convention %s ne doit plus se modifier", statut)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ApiErrorCode.DEMANDE_NON_EDITABLE));
        }
    }

    @Test
    @Transactional
    void leRefusLaisseLaConventionIntacte() {
        Convention c = convention(mienne, mienne, StatutConvention.VALIDE);
        String avant = c.getIntitule();

        // Le texte exact : le front l'affiche tel quel, il ne doit pas changer par accident.
        assertThatThrownBy(() -> service.update(c.getId(), modification(c.getReference(), "Trop tard"), ac))
                .isInstanceOf(ApiException.class)
                .hasMessage("Modification impossible : la convention a déjà été validée par la DGB");

        assertThat(conventionRepository.findById(c.getId()).orElseThrow().getIntitule()).isEqualTo(avant);
    }

    // ── l'annulation par l'autorité ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laTitulaireAnnuleSaConventionEnAttente() {
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);

        service.annulerParAutorite(c.getId(), "  Saisie en double  ", ac);

        Convention apres = conventionRepository.findById(c.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutConvention.ANNULEE);
        assertThat(apres.getMotifRejet()).isEqualTo("Saisie en double");
    }

    @Test
    @Transactional
    void laCreatriceAnnuleLaConventionQuElleACreePourUneAutre() {
        Convention c = convention(autreAutorite(), mienne, StatutConvention.EN_ATTENTE);

        service.annulerParAutorite(c.getId(), null, ac);

        assertThat(conventionRepository.findById(c.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutConvention.ANNULEE);
    }

    @Test
    @Transactional
    void uneAutreAutoriteNAnnulePas() {
        AutoriteContractante autre = autreAutorite();
        Convention c = convention(autre, autre, StatutConvention.EN_ATTENTE);

        assertThatThrownBy(() -> service.annulerParAutorite(c.getId(), null, ac))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        assertThat(conventionRepository.findById(c.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutConvention.EN_ATTENTE);
    }

    @Test
    @Transactional
    void lAutoriteNAnnulePlusUneConventionValideeOuRejetee() {
        // Le cas qui justifie la route à part : passée la validation, l'annulation n'appartient
        // plus à l'autorité. Les directions gardent la leur par la route de statut.
        for (StatutConvention statut : List.of(StatutConvention.VALIDE, StatutConvention.REJETE,
                StatutConvention.ANNULEE)) {
            Convention c = convention(mienne, mienne, statut);

            assertThatThrownBy(() -> service.annulerParAutorite(c.getId(), null, ac))
                    .as("une convention %s ne doit pas s'annuler par l'autorité", statut)
                    .isInstanceOf(ApiException.class)
                    .hasMessageStartingWith("Annulation impossible")
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ApiErrorCode.DEMANDE_NON_EDITABLE));
        }
    }

    @Test
    @Transactional
    void uneDirectionNAnnulePasParCetteRoute() {
        Utilisateur dgb = utilisateurRepository.findByUsername("dgb").orElseThrow();
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);
        AuthenticatedUser direction = new AuthenticatedUser(dgb.getId(), dgb.getUsername(), Role.DGB);

        assertThatThrownBy(() -> service.annulerParAutorite(c.getId(), null, direction))
                .isInstanceOf(ApiException.class)
                .hasMessage("Seule l'autorité contractante titulaire ou créatrice peut annuler une convention");
    }

    @Test
    @Transactional
    void leRelaisAnnuleCommeLAutoriteRepresentee() {
        Utilisateur relais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        AuthenticatedUser enRelais = new AuthenticatedUser(relais.getId(), relais.getUsername(),
                Role.AUTORITE_CONTRACTANTE, true, null, mienne.getId());
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);

        service.annulerParAutorite(c.getId(), null, enRelais);

        assertThat(conventionRepository.findById(c.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutConvention.ANNULEE);
    }

    // ── la référence ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laReferenceResteUnique() {
        Convention autre = convention(mienne, mienne, StatutConvention.EN_ATTENTE);
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);

        assertThatThrownBy(() -> service.update(c.getId(), modification(autre.getReference(), "Doublon"), ac))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà utilisée");
    }

    @Test
    @Transactional
    void garderSaPropreReferenceNEstPasUnDoublon() {
        Convention c = convention(mienne, mienne, StatutConvention.EN_ATTENTE);

        assertThatCode(() -> service.update(c.getId(), modification(c.getReference(), "Même référence"), ac))
                .doesNotThrowAnyException();
    }

    // ── la permission et la route ──────────────────────────────────────────────────────────────

    @Test
    void lesPermissionsSontAccordeesAuxTroisRolesDAutoriteSeulement() {
        for (String permission : List.of("convention.update", "convention.annuler")) {
            for (Role role : List.of(Role.AUTORITE_CONTRACTANTE, Role.AUTORITE_UPM, Role.AUTORITE_UEP)) {
                assertThat(permissionService.findPermissionCodesByRole(role))
                        .as("le rôle %s doit détenir %s", role, permission).contains(permission);
            }
            for (Role role : List.of(Role.ENTREPRISE, Role.DGB, Role.DGD, Role.DGI, Role.DGTCP)) {
                assertThat(permissionService.findPermissionCodesByRole(role))
                        .as("le rôle %s ne doit pas détenir %s", role, permission).doesNotContain(permission);
            }
        }
    }

    @Test
    void lesRoutesExigentLeursPermissions() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ac, null, List.of(new SimpleGrantedAuthority("convention.view"))));
        try {
            assertThatThrownBy(() -> controller.update(1L, modification("REF", "x"), ac))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.annuler(1L, null, ac))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UpdateConventionRequest modification(String reference, String intitule) {
        return UpdateConventionRequest.builder()
                .reference(reference)
                .intitule(intitule)
                .dateSignature(LocalDate.of(2026, 1, 15))
                .dateFin(LocalDate.of(2030, 12, 31))
                .montantMru(new BigDecimal("2500000"))
                .build();
    }

    private AutoriteContractante autreAutorite() {
        return autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité tierce " + System.nanoTime())
                .code("AC-MOD-" + System.nanoTime())
                .build());
    }

    private Convention convention(AutoriteContractante titulaire, AutoriteContractante creatrice,
                                  StatutConvention statut) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference("CONV-MOD-" + System.nanoTime())
                .intitule("Convention à corriger")
                .statut(statut)
                .autoriteContractante(titulaire)
                .creeParAutoriteContractante(creatrice)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
    }
}
