package mr.gov.finances.sgci.config;

import org.springframework.core.env.Environment;

import java.util.List;
import java.util.stream.Stream;

/**
 * Origines explicites du front, selon le profil. Pas de slash final.
 * Avec {@code allowCredentials(true)}, il ne faut ni {@code *} ni motifs du type {@code http://host:*}
 * (Spring peut rejeter la config).
 * <p>
 * En production, le seul domaine officiel (restriction posée le 2026-10-31). Hors production, aussi
 * les aperçus Lovable et le front local : sans eux, on ne peut ni développer ni prévisualiser.
 * <p>
 * Tunnel ngrok vers l’API : si le navigateur reçoit une page d’avertissement ngrok sans en-têtes CORS,
 * envoyer depuis le front l’en-tête {@code ngrok-skip-browser-warning: true} sur les requêtes vers l’URL ngrok.
 */
public final class CorsAllowedOrigins {

    private CorsAllowedOrigins() {}

    /**
     * Le front et l'API partagent ce domaine, mais derrière nginx l'application voit une requête
     * http : pour Spring, l'origine https est donc étrangère, et une origine absente de cette liste
     * fait refuser la connexion (403 « Invalid CORS request », le 2026-10-27).
     */
    public static final List<String> PRODUCTION = List.of(
            "https://commission-fiscale.gov.mr"
    );

    public static final List<String> DEVELOPPEMENT = Stream.concat(PRODUCTION.stream(), Stream.of(
            "https://e6e2ee9f-7068-4371-95aa-8f4613a60836.lovableproject.com",
            "https://id-preview--e6e2ee9f-7068-4371-95aa-8f4613a60836.lovable.app",
            "https://commission-fiscale.lovable.app",
            "https://e6b3-197-231-5-85.ngrok-free.app",
            "http://localhost:8081",
            "https://cftest2.lovable.app",
            "http://127.0.0.1:8081",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://178.128.171.174:8081",
            "http://10.16.0.5:8081",
            "http://10.106.0.2:8081",
            "http://172.18.0.1:8081",
            "http://10.1.230.60:8082",
            "http://46.202.171.85:8080"
    )).toList();

    public static List<String> pour(Environment env) {
        return ExigencesProduction.estProduction(env) ? PRODUCTION : DEVELOPPEMENT;
    }

    public static boolean autorisee(Environment env, String origineNettoyee) {
        return origineNettoyee != null && pour(env).contains(origineNettoyee);
    }
}
