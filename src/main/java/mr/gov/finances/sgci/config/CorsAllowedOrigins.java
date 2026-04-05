package mr.gov.finances.sgci.config;

import java.util.List;

/**
 * Origines du front Vite (dev). Pas de slash final — requis par la spec CORS.
 * Ajouter ici les URLs de prod quand elles sont connues.
 */
public final class CorsAllowedOrigins {

    private CorsAllowedOrigins() {}

    public static final List<String> FRONTEND = List.of(
            "http://localhost:8081",
            "http://178.128.171.174:8081",
            "http://10.16.0.5:8081",
            "http://10.106.0.2:8081",
            "http://172.18.0.1:8081"
    );
}
