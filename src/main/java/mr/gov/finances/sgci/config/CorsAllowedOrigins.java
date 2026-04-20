package mr.gov.finances.sgci.config;

import java.util.List;

/**
 * Origines explicites du front (Vite). Pas de slash final.
 * Avec {@code allowCredentials(true)}, il ne faut ni {@code *} ni motifs du type {@code http://host:*}
 * (Spring peut rejeter la config). Ajouter ici les URLs de prod si besoin.
 * <p>
 * Tunnel ngrok vers l’API : si le navigateur reçoit une page d’avertissement ngrok sans en-têtes CORS,
 * envoyer depuis le front l’en-tête {@code ngrok-skip-browser-warning: true} sur les requêtes vers l’URL ngrok.
 */
public final class CorsAllowedOrigins {

    private CorsAllowedOrigins() {}

    public static final List<String> FRONTEND = List.of(
            "https://e6e2ee9f-7068-4371-95aa-8f4613a60836.lovableproject.com",
            "https://id-preview--e6e2ee9f-7068-4371-95aa-8f4613a60836.lovable.app",
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
