package mr.gov.finances.sgci.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.service.PermissionService;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = extractAuthorizationHeader(request);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (jwtService.isTokenValid(token)) {
            String username = jwtService.extractUsername(token);
            Role role = jwtService.extractRole(token);
            Long userId = jwtService.extractUserId(token);
            List<String> permissions = jwtService.extractPermissions(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));

                Set<String> mergedPermissions = new LinkedHashSet<>();
                if (permissions != null) {
                    mergedPermissions.addAll(permissions);
                }
                mergedPermissions.addAll(permissionService.findPermissionCodesByRole(role));

                for (String permission : mergedPermissions) {
                    authorities.add(new SimpleGrantedAuthority(permission));
                }
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, username, role),
                        null,
                        authorities
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Remonte la chaîne de wrappers (multipart, caching, etc.) pour lire Authorization,
     * car certains conteneurs ou filtres enveloppent la requête avant le filtre JWT.
     */
    private String extractAuthorizationHeader(HttpServletRequest request) {
        HttpServletRequest current = request;
        for (int depth = 0; depth < 16 && current != null; depth++) {
            String h = current.getHeader(AUTHORIZATION_HEADER);
            if (h != null && !h.isBlank()) {
                return h.trim();
            }
            if (current instanceof HttpServletRequestWrapper wrapper) {
                current = (HttpServletRequest) wrapper.getRequest();
            } else {
                break;
            }
        }
        return null;
    }
}
