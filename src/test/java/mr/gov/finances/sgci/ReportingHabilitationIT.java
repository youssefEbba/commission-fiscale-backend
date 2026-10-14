package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.web.controller.ReportingController;
import mr.gov.finances.sgci.web.dto.reporting.ReportingSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qui a le droit de lire le reporting, et ce qu'il voit quand il n'a aucun périmètre.
 *
 * <p>Défaut constaté : {@code GET /api/reporting/summary} ne portait aucun {@code @PreAuthorize},
 * alors que {@code COMMISSION_RELAIS} est le seul des douze rôles à qui {@code reporting.view} est
 * refusée. Et comme un relais n'a ni entreprise ni autorité contractante rattachée, il tombait dans
 * la branche de repli du cadrage, où aucun filtre n'était posé : il ressortait avec les
 * <strong>totaux nationaux</strong>. Le rôle explicitement privé de reporting était celui qui
 * voyait tout.
 *
 * <p>Deux verrous, parce qu'un seul ne suffit pas : l'annotation sur la route, et le périmètre
 * fermé dans le cadrage. Sans le second, le service resterait fuyant pour tout futur appelant — un
 * traitement différé, un export, un autre contrôleur.
 *
 * <p>Les contrôles d'accès vivent en {@code @PreAuthorize} : invérifiables par un test de service,
 * et les tests web ne démarrent pas sur ce poste. {@code @EnableMethodSecurity} étant actif, un
 * appel direct au bean du contrôleur est intercepté dès qu'une authentification est posée.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportingHabilitationIT {

    /** Large, pour que le test ne dépende pas des dates du jeu de démonstration. */
    private static final Instant DEBUT = Instant.now().minus(7300, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private ReportingController controller;

    @Autowired
    private ReportingService service;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    // ── le défaut rapporté ─────────────────────────────────────────────────────────────────────

    @Test
    void leRelaisNonImpersonateNeVoitPasLesTotauxNationaux() {
        AuthenticatedUser relais = authentifier("commission_relais", Role.COMMISSION_RELAIS);

        assertThatThrownBy(() -> controller.summary(relais, DEBUT, FIN, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void leRelaisNaPasReportingView() {
        // Assertion compagnonne : sans elle, quelqu'un « corrige » le test ci-dessus en accordant
        // la permission au relais, ce qui aggrave le défaut au lieu de le réparer.
        List<String> permissions = new ArrayList<>(
                permissionService.findPermissionCodesByRole(Role.COMMISSION_RELAIS));

        assertThat(permissions).doesNotContain("reporting.view");
    }

    @Test
    void lePerimetreEstFermeSansEntrepriseNiAutorite() {
        // Second verrou, en contournant l'annotation : le service est appelé directement, comme le
        // ferait un export ou un traitement différé. Il doit rendre des zéros, pas le national.
        Utilisateur relais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        assertThat(relais.getEntreprise()).isNull();
        assertThat(relais.getAutoriteContractante()).isNull();
        AuthenticatedUser principal = new AuthenticatedUser(
                relais.getId(), relais.getUsername(), Role.COMMISSION_RELAIS);

        ReportingSummaryDto vu = service.getSummary(principal, DEBUT, FIN, null, null);
        ReportingSummaryDto national = service.getSummary(
                principalDe("president", Role.PRESIDENT), DEBUT, FIN, null, null);

        assertThat(vu.getDemandes().getTotal()).isZero();
        assertThat(vu.getCertificatsTotal()).isZero();
        assertThat(vu.getUtilisationsTotal()).isZero();
        assertThat(vu.getCertificatFinancials().getSumMontantCordon())
                .isEqualByComparingTo(BigDecimal.ZERO);
        // Et le national n'est pas vide : sans cela, les zéros ci-dessus ne prouveraient rien.
        assertThat(national.getDemandes().getTotal()).isPositive();
    }

    // ── ce qui doit continuer de passer ────────────────────────────────────────────────────────

    @Test
    void lesRolesPorteursDeLaPermissionPassent() {
        // Attrape un @PreAuthorize écrit avec le mauvais code de permission — erreur qui, sinon, ne
        // se voit qu'en recette, et seulement pour ceux qui essaient.
        for (String compte : List.of("president", "admin", "dgd", "dgtcp", "dgi", "dgb",
                "ac", "entreprise")) {
            Utilisateur u = utilisateurRepository.findByUsername(compte).orElseThrow();
            AuthenticatedUser acteur = authentifier(compte, u.getRole());

            assertThat(permissionService.findPermissionCodesByRole(u.getRole()))
                    .as("le compte %s doit détenir reporting.view", compte)
                    .contains("reporting.view");
            assertThatCode(() -> controller.summary(acteur, DEBUT, FIN, null, null))
                    .as("compte %s", compte)
                    .doesNotThrowAnyException();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void leRelaisImpersonantUneEntrepriseVoitSonPerimetre() {
        // L'impersonation délivre le rôle de la cible, pas celui du relais. Ce test attrape un
        // durcissement du cadrage fait à la hache, qui casserait le support aux utilisateurs.
        Utilisateur relais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        Entreprise cible = compteEntreprise.getEntreprise();
        assertThat(cible).isNotNull();

        AuthenticatedUser impersonant = new AuthenticatedUser(
                relais.getId(), relais.getUsername(), Role.ENTREPRISE, true, cible.getId(), null);
        poser(impersonant, Role.ENTREPRISE);

        ReportingSummaryDto vuParLeRelais = controller.summary(impersonant, DEBUT, FIN, null, null);
        SecurityContextHolder.clearContext();
        ReportingSummaryDto vuParLEntreprise = service.getSummary(
                principalDe("entreprise", Role.ENTREPRISE), DEBUT, FIN, null, null);

        assertThat(vuParLeRelais.getDemandes().getTotal())
                .isEqualTo(vuParLEntreprise.getDemandes().getTotal());
        assertThat(vuParLeRelais.getCertificatsTotal())
                .isEqualTo(vuParLEntreprise.getCertificatsTotal());
    }

    @Test
    void lAutoriteSansRattachementEstRefusee() {
        // Le refus existant doit survivre à l'extraction du cadrage : une autorité contractante
        // sans rattachement n'a pas de périmètre, et on ne lui en invente pas un.
        Utilisateur orpheline = utilisateurRepository.save(Utilisateur.builder()
                .username("ac-sans-rattachement-" + System.nanoTime())
                .passwordHash("x")
                .role(Role.AUTORITE_CONTRACTANTE)
                .nomComplet("AC orpheline")
                .email("orpheline-" + System.nanoTime() + "@sharklasers.com")
                .actif(true)
                .build());
        AuthenticatedUser principal = new AuthenticatedUser(
                orpheline.getId(), orpheline.getUsername(), Role.AUTORITE_CONTRACTANTE);

        assertThatThrownBy(() -> service.getSummary(principal, DEBUT, FIN, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Autorité contractante requise");
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser principalDe(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), role);
    }

    /** Pose dans le contexte de sécurité une authentification identique à celle du filtre JWT. */
    private AuthenticatedUser authentifier(String username, Role role) {
        AuthenticatedUser principal = principalDe(username, role);
        poser(principal, role);
        return principal;
    }

    private void poser(AuthenticatedUser principal, Role role) {
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionService.findPermissionCodesByRole(role)
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
