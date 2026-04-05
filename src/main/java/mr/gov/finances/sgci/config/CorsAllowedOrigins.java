package mr.gov.finances.sgci.config;

import java.util.List;

/**
 * Origines explicites du front (Vite). Pas de slash final.
 * Avec {@code allowCredentials(true)}, il ne faut ni {@code *} ni motifs du type {@code http://host:*}
 * (Spring peut rejeter la config). Ajouter ici les URLs de prod si besoin.
 */
public final class CorsAllowedOrigins {

    private CorsAllowedOrigins() {}

    public static final List<String> FRONTEND = List.of(
            "http://localhost:8081",
            "http://127.0.0.1:8081",
            "http://178.128.171.174:8081",
            "http://10.16.0.5:8081",
            "http://10.106.0.2:8081",
            "http://172.18.0.1:8081"
    );

    public static boolean matchesFrontendOrigin(String originTrimmed) {
        return originTrimmed != null && FRONTEND.contains(originTrimmed);
    }
}
