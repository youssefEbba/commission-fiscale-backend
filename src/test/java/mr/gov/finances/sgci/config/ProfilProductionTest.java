package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le profil de production n'expose rien de ce qui sert au développement.
 *
 * <p>Constats de la revue de sécurité : la documentation Swagger de toute l'API était ouverte sans
 * authentification, les journaux de Spring Web et Spring Security étaient en DEBUG dans le profil
 * commun, et rien n'interdisait au serveur d'inclure message ou pile d'appels dans ses pages
 * d'erreur.
 *
 * <p>Test unitaire : il lit les fichiers de configuration tels qu'ils seront livrés.
 */
class ProfilProductionTest {

    @Test
    void swaggerEstFermeEnProduction() throws IOException {
        Properties prod = lire("application-prod.properties");

        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void lesPagesDErreurNeDetaillentRienEnProduction() throws IOException {
        Properties prod = lire("application-prod.properties");

        assertThat(prod.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-exception")).isEqualTo("false");
    }

    @Test
    void aucunJournalEnDebugHorsPosteDeDeveloppement() throws IOException {
        for (String fichier : new String[] {"application.properties", "application-prod.properties"}) {
            lire(fichier).forEach((cle, valeur) -> {
                if (cle.toString().startsWith("logging.level.")) {
                    assertThat(valeur.toString().trim()).as(fichier + " : " + cle).isNotIn("DEBUG", "TRACE");
                }
            });
        }
    }

    @Test
    void unJetonDeConnexionNeDurePasPlusQuUneJourneeDeTravail() throws IOException {
        long huitHeures = 8L * 60 * 60 * 1000;
        assertThat(Long.parseLong(lire("application.properties").getProperty("app.jwt.expiration-ms")))
                .isLessThanOrEqualTo(huitHeures);
        assertThat(new JwtProperties().getExpirationMs()).isLessThanOrEqualTo(huitHeures);
    }

    @Test
    void aucunSecretNEstEcritDansLaConfigurationLivree() throws IOException {
        for (String fichier : new String[] {"application.properties", "application-prod.properties"}) {
            Properties p = lire(fichier);
            for (String cle : new String[] {"app.jwt.secret", "spring.datasource.password", "minio.access-key",
                    "minio.secret-key", "app.exchangerate.host.access-key", "spring.mail.password"}) {
                String valeur = p.getProperty(cle);
                if (valeur != null) {
                    // Vide, ou une variable d'environnement sans valeur par défaut.
                    assertThat(valeur).as(fichier + " : " + cle).matches("(\\$\\{[A-Z_]+:})?");
                }
            }
        }
    }

    private static Properties lire(String fichier) throws IOException {
        Properties p = new Properties();
        try (InputStream in = ProfilProductionTest.class.getClassLoader().getResourceAsStream(fichier)) {
            assertThat(in).as(fichier).isNotNull();
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }
}
