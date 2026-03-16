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

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, Role role, Long userId, Collection<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("userId", userId)
                .claim("permissions", permissions != null ? List.copyOf(permissions) : List.of())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
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
