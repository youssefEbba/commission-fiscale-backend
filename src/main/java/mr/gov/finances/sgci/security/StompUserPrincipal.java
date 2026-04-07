package mr.gov.finances.sgci.security;

import lombok.Getter;
import mr.gov.finances.sgci.domain.enums.Role;

import java.security.Principal;

/**
 * Identité STOMP après validation du JWT sur la trame CONNECT (temps réel).
 */
@Getter
public class StompUserPrincipal implements Principal {

    private final Long userId;
    private final String username;
    private final Role role;

    public StompUserPrincipal(Long userId, String username, Role role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    @Override
    public String getName() {
        return username != null ? username : String.valueOf(userId);
    }
}
