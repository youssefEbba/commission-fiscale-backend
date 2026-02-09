package mr.gov.finances.sgci.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mr.gov.finances.sgci.domain.enums.Role;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private final Long userId;
    private final String username;
    private final Role role;
}
