package mr.gov.finances.sgci.config;

import java.util.List;

/**
 * CORS pour le front Vite (dev). On utilise des motifs (port variable) pour éviter
 * les écarts entre localhost / réseau / Docker.
 * <p>
 * Avec {@code allowCredentials(true)}, Spring réfléchit l'origine réelle de la requête
 * lorsque le motif correspond (plus fiable que {@code *} + credentials).
 */
public final class CorsAllowedOrigins {

    private CorsAllowedOrigins() {}

    /** Motifs acceptés pour Access-Control-Allow-Origin (REST + SockJS /ws). */
    public static final List<String> FRONTEND_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://178.128.171.174:*",
            "http://10.16.0.5:*",
            "http://10.106.0.2:*",
            "http://172.18.0.1:*"
    );
}
