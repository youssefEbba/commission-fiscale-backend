package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.DecisionTransfertCreditController;
import mr.gov.finances.sgci.web.controller.TransfertCreditController;
import mr.gov.finances.sgci.web.dto.DecisionCreditRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les quatre rôles du circuit de transfert peuvent voir ce qu'ils doivent viser.
 *
 * <p>Défaut constaté en recette : la DGD ne détient que {@code transfert.dgd.visa}, et les routes de
 * lecture n'acceptaient que {@code solde.view}, {@code dgtcp.queue.view}, les deux permissions du
 * Président et {@code archivage.view}. La DGD recevait donc 403 sur la liste — « Impossible de
 * charger les transferts » — alors qu'elle est <strong>première</strong> dans {@code ORDRE_VISAS} :
 * le circuit entier était bloqué à son premier pas, personne ne pouvant viser après elle.
 *
 * <p>La DGI ne passait que par raccroc, parce que son bloc de permissions contient
 * {@code archivage.view} pour une raison sans rapport. Ce test fige les deux.
 *
 * <p>Les contrôles d'accès vivent en {@code @PreAuthorize} : invérifiables par un test de service, et
 * les tests web ne démarrent pas sur ce poste. {@code @EnableMethodSecurity} étant actif, un appel
 * direct au bean du contrôleur est intercepté dès qu'une authentification est posée dans le contexte.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransfertAccesCircuitIT {

    @Autowired
    private TransfertCreditController controller;

    @Autowired
    private DecisionTransfertCreditController decisionController;

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
    void laDgdPeutListerLesTransferts() {
        AuthenticatedUser dgd = authentifier("dgd", Role.DGD);

        assertThatCode(() -> controller.getAll(dgd)).doesNotThrowAnyException();
    }

    @Test
    void laDgdNeDetientPourtantQueSaPermissionDeVisa() {
        // La correction ne consiste pas à lui accorder une permission de plus : c'est la route qui
        // devait reconnaître celle qu'elle a déjà. Si ce test tombe, quelqu'un a élargi le rôle.
        List<String> permissions = new ArrayList<>(permissionService.findPermissionCodesByRole(Role.DGD));

        assertThat(permissions).contains("transfert.dgd.visa");
        assertThat(permissions).doesNotContain(
                "transfert.solde.view", "transfert.dgtcp.queue.view", "transfert.president.validate");
    }

    @Test
    void lesQuatreRolesDuCircuitPeuventLister() {
        for (Role role : List.of(Role.DGD, Role.DGI, Role.DGTCP, Role.PRESIDENT)) {
            AuthenticatedUser acteur = authentifier(compte(role), role);

            assertThatCode(() -> controller.getAll(acteur)).as("rôle %s", role).doesNotThrowAnyException();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void lentrepriseContinueDeVoirLesSiens() {
        AuthenticatedUser entreprise = authentifier("entreprise", Role.ENTREPRISE);

        assertThatCode(() -> controller.getAll(entreprise)).doesNotThrowAnyException();
    }

    // ── le rejet temporaire, que le service autorisait déjà aux quatre rôles ───────────────────

    @Test
    void laDgdPeutEmettreUnRejetTemporaire() {
        AuthenticatedUser dgd = authentifier("dgd", Role.DGD);

        // L'appel échouera sur le fond — le transfert 999 n'existe pas — mais plus sur l'accès.
        // C'est exactement ce que le test doit distinguer : la porte, pas le dossier.
        assertThatThrownBy(() -> decisionController.saveDecision(999L, rejetTemporaire(), dgd))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unRoleHorsCircuitResteRefuse() {
        // Sans cette assertion, on ne saurait pas si la porte a été ouverte à tout le monde.
        AuthenticatedUser entreprise = authentifier("entreprise", Role.ENTREPRISE);

        assertThatThrownBy(() -> decisionController.saveDecision(999L, rejetTemporaire(), entreprise))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private DecisionCreditRequest rejetTemporaire() {
        DecisionCreditRequest req = new DecisionCreditRequest();
        req.setDecision(DecisionCorrectionType.REJET_TEMP);
        req.setMotifRejet("Pièce manquante");
        req.setDocumentsDemandes(List.of("CONVENTION"));
        return req;
    }

    private String compte(Role role) {
        return switch (role) {
            case DGD -> "dgd";
            case DGI -> "dgi";
            case DGTCP -> "dgtcp";
            case PRESIDENT -> "president";
            default -> throw new IllegalArgumentException("Pas de compte amorcé pour " + role);
        };
    }

    /** Pose dans le contexte de sécurité une authentification identique à celle du filtre JWT. */
    private AuthenticatedUser authentifier(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionService.findPermissionCodesByRole(role)
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getUsername(), role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        return principal;
    }
}
