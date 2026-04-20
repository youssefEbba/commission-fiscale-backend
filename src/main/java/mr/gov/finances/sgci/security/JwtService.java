package mr.gov.finances.sgci.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.config.JwtProperties;
import mr.gov.finances.sgci.domain.enums.Role;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_IMPERSONATING = "impersonating";
    private static final String CLAIM_ACTING_ENTREPRISE_ID = "actingEntrepriseId";
    private static final String CLAIM_ACTING_AC_ID = "actingAutoriteContractanteId";

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, Role role, Long userId, Collection<String> permissions) {
        return generateToken(username, role, userId, permissions, false, null, null, jwtProperties.getExpirationMs());
    }

    /**
     * @param expirationMs durée de validité du jeton (ex. impersonation : {@link JwtProperties#getRelaisExpirationMs()}).
     */
    public String generateToken(String username, Role role, Long userId, Collection<String> permissions,
                              boolean impersonating, Long actingEntrepriseId, Long actingAutoriteContractanteId,
                              long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("userId", userId)
                .claim("permissions", permissions != null ? List.copyOf(permissions) : List.of())
                .claim(CLAIM_IMPERSONATING, impersonating)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey());
        if (actingEntrepriseId != null) {
            builder.claim(CLAIM_ACTING_ENTREPRISE_ID, actingEntrepriseId);
        }
        if (actingAutoriteContractanteId != null) {
            builder.claim(CLAIM_ACTING_AC_ID, actingAutoriteContractanteId);
        }
        return builder.compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Number userId = getClaims(token).get("userId", Number.class);
        return userId != null ? userId.longValue() : null;
    }

    public Role extractRole(String token) {
        String role = getClaims(token).get("role", String.class);
        return role != null ? Role.valueOf(role) : null;
    }

    public boolean extractImpersonating(String token) {
        Boolean v = getClaims(token).get(CLAIM_IMPERSONATING, Boolean.class);
        return Boolean.TRUE.equals(v);
    }

    public Long extractActingEntrepriseId(String token) {
        Number n = getClaims(token).get(CLAIM_ACTING_ENTREPRISE_ID, Number.class);
        return n != null ? n.longValue() : null;
    }

    public Long extractActingAutoriteContractanteId(String token) {
        Number n = getClaims(token).get(CLAIM_ACTING_AC_ID, Number.class);
        return n != null ? n.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object value = getClaims(token).get("permissions");
        if (value instanceof List<?> list) {
            List<String> permissions = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    permissions.add(item.toString());
                }
            }
            return permissions;
        }
        return List.of();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
