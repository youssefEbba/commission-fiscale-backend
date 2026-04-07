package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.JwtService;
import mr.gov.finances.sgci.security.StompUserPrincipal;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authentification WebSocket alignée sur le REST : JWT sur la trame STOMP {@code CONNECT}
 * (en-têtes {@code Authorization: Bearer …} ou {@code token: …}), même règle compte actif que le filtre HTTP.
 * Limite l’abonnement {@code /topic/notifications/user/{id}} au seul utilisateur {@code id}.
 */
@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern USER_NOTIFICATION_TOPIC = Pattern.compile("^/topic/notifications/user/(\\d+)$");

    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearer(accessor);
            if (token == null || !jwtService.isTokenValid(token)) {
                throw new StompAuthException("WebSocket: JWT manquant, expiré ou invalide (CONNECT avec Authorization: Bearer ou en-tête token)");
            }
            Long userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);
            Role role = jwtService.extractRole(token);
            if (userId != null) {
                Utilisateur account = utilisateurRepository.findById(userId).orElse(null);
                if (account == null || Boolean.FALSE.equals(account.getActif())) {
                    throw new StompAuthException("WebSocket: compte désactivé ou introuvable");
                }
            }
            Principal principal = new StompUserPrincipal(userId, username, role);
            accessor.setUser(principal);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (!(user instanceof StompUserPrincipal sup) || sup.getUserId() == null) {
                throw new StompAuthException("WebSocket: authentification requise pour s'abonner");
            }
            String dest = accessor.getDestination();
            if (dest != null) {
                Matcher m = USER_NOTIFICATION_TOPIC.matcher(dest);
                if (m.matches()) {
                    long wanted = Long.parseLong(m.group(1));
                    if (sup.getUserId() != wanted) {
                        throw new StompAuthException("WebSocket: abonnement refusé à ce canal notifications");
                    }
                }
            }
        }

        return message;
    }

    private static String extractBearer(StompHeaderAccessor accessor) {
        String auth = firstNative(accessor, "Authorization");
        if (auth == null) {
            auth = firstNative(accessor, "authorization");
        }
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return firstNative(accessor, "token");
    }

    private static String firstNative(StompHeaderAccessor accessor, String name) {
        List<String> headers = accessor.getNativeHeader(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String v = headers.get(0);
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    /** Erreur métier STOMP (convertie en trame ERROR côté broker). */
    public static final class StompAuthException extends RuntimeException {
        public StompAuthException(String message) {
            super(message);
        }
    }
}
