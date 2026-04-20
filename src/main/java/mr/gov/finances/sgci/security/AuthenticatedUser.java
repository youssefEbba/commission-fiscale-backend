package mr.gov.finances.sgci.security;

import lombok.Getter;
import mr.gov.finances.sgci.domain.enums.Role;

/**
 * Principal Spring Security. {@link #getRole()} reflète le rôle JWT (rôle effectif en cas d’impersonation).
 */
@Getter
public class AuthenticatedUser {

    private final Long userId;
    private final String username;
    private final Role role;
    private final boolean impersonating;
    private final Long actingEntrepriseId;
    private final Long actingAutoriteContractanteId;

    public AuthenticatedUser(Long userId, String username, Role role) {
        this(userId, username, role, false, null, null);
    }

    public AuthenticatedUser(Long userId, String username, Role role,
                             boolean impersonating, Long actingEntrepriseId, Long actingAutoriteContractanteId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.impersonating = impersonating;
        this.actingEntrepriseId = actingEntrepriseId;
        this.actingAutoriteContractanteId = actingAutoriteContractanteId;
    }
}
