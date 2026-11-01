package mr.gov.finances.sgci.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Les secrets qu'un serveur de production doit recevoir de son environnement, et jamais du code.
 *
 * <p>La clé de signature des jetons, le mot de passe MySQL et les identifiants MinIO avaient des
 * valeurs par défaut écrites dans les fichiers versionnés : un serveur démarré sans variable
 * d'environnement tournait avec des secrets que quiconque a lu le dépôt connaît. Ces valeurs sont
 * listées ici pour être refusées si elles réapparaissent par une variable mal recopiée.
 *
 * <p>Un manquement nomme la variable à définir, jamais la valeur trouvée : le message part dans le
 * journal du serveur.
 */
public final class ExigencesProduction {

    /** HS256 exige une clé d'au moins 256 bits. */
    static final int OCTETS_MIN_CLE_JWT = 32;

    /** Valeurs publiées dans l'historique du dépôt : elles ne protègent plus rien. */
    private static final Set<String> SECRETS_PUBLIES = Set.of(
            "SGCI-Secret-Key-Minimum-256-bits-For-HS256-Please-Change-In-Production",
            "X7mD!r8KpE6@Z5YVQ#s1A4dFJ0Uo$B9%2H^tWCLN*eG",
            "Admin@1234",
            "RootPassword@2026",
            "admin",
            "password123",
            "123456");

    private ExigencesProduction() {
    }

    public static boolean estProduction(Environment env) {
        return env.acceptsProfiles(Profiles.of("prod"));
    }

    /** Ce qui empêche ce serveur de démarrer en production ; vide hors production. */
    public static List<String> manquements(Environment env) {
        List<String> manquements = new ArrayList<>();
        if (!estProduction(env)) {
            return manquements;
        }
        String cleJwt = env.getProperty("app.jwt.secret", "");
        if (inutilisable(cleJwt)) {
            manquements.add("APP_JWT_SECRET : clé de signature des jetons absente ou déjà publiée");
        } else if (cleJwt.getBytes(StandardCharsets.UTF_8).length < OCTETS_MIN_CLE_JWT) {
            manquements.add("APP_JWT_SECRET : clé trop courte, il faut au moins 256 bits (32 caractères)");
        }
        if (inutilisable(env.getProperty("spring.datasource.password", ""))) {
            manquements.add("SPRING_DATASOURCE_PASSWORD : mot de passe de la base absent ou déjà publié");
        }
        if (!"local".equalsIgnoreCase(env.getProperty("app.storage.backend", "minio"))) {
            if (inutilisable(env.getProperty("minio.access-key", ""))) {
                manquements.add("MINIO_ACCESS_KEY : identifiant MinIO absent ou déjà publié");
            }
            if (inutilisable(env.getProperty("minio.secret-key", ""))) {
                manquements.add("MINIO_SECRET_KEY : clé MinIO absente ou déjà publiée");
            }
        }
        return manquements;
    }

    /** Vide, ou connu de quiconque a lu le dépôt. */
    static boolean inutilisable(String valeur) {
        return valeur == null || valeur.isBlank() || SECRETS_PUBLIES.contains(valeur.trim());
    }
}
