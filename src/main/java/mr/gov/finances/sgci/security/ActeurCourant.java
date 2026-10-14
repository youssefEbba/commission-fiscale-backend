package mr.gov.finances.sgci.security;

import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Qui agit, au moment où l'on écrit une trace.
 *
 * <p>Extrait d'{@code AuditService}, qui portait ce bloc en ligne. Il est maintenant partagé avec
 * l'historisation des statuts : deux traces écrites du même geste doivent nommer le même acteur,
 * sinon les rapprocher devient un exercice de divination.
 *
 * <p>Hors contexte de sécurité — migrations de démarrage, traitements {@code @Async} — l'acteur est
 * {@code system}. Ce n'est pas un défaut à corriger : c'est l'information juste.
 *
 * <p>Utilitaire statique, délibérément : il est appelé depuis un écouteur Hibernate, qui ne doit
 * détenir aucun bean Spring sous peine de cycle à la création de l'{@code EntityManagerFactory}.
 */
public final class ActeurCourant {

    /** L'acteur par défaut, quand aucune authentification n'est posée. */
    public static final String SYSTEME = "system";

    private ActeurCourant() {
    }

    /**
     * @param userId {@code null} hors authentification, ou pour un principal d'un autre type
     * @param role   {@code null} hors authentification ; en impersonation, le rôle effectif porté
     *               par le jeton, donc celui sous lequel le geste a réellement été accompli
     */
    public record Acteur(Long userId, String username, Role role) {
    }

    public static Acteur lire() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return new Acteur(user.getUserId(), user.getUsername(), user.getRole());
        }
        if (auth != null && !"anonymousUser".equals(auth.getPrincipal())) {
            return new Acteur(null, auth.getName(), null);
        }
        return new Acteur(null, SYSTEME, null);
    }
}
