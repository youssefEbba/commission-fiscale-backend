package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * En production, aucun secret ne vient du code.
 *
 * <p>Constat de la revue de sécurité : la clé de signature des jetons, le mot de passe MySQL et les
 * identifiants MinIO avaient des valeurs par défaut écrites dans les fichiers versionnés. Un
 * serveur démarré sans variable d'environnement tournait avec des secrets que quiconque a lu le
 * dépôt connaît. Le serveur de production refuse désormais de démarrer dans ce cas.
 *
 * <p>Test unitaire : la règle ne dépend que de la configuration lue.
 */
class ExigencesProductionTest {

    private static final String SECRET_SOLIDE = "k3Vq9yZr0bT2xW8nL5cJ7hP1sD4fG6aM-production-uniquement";

    @Test
    void uneProductionCompleteEstAcceptee() {
        assertThat(ExigencesProduction.manquements(production())).isEmpty();
    }

    @Test
    void sansCleDeSignatureLaProductionNeDemarrePas() {
        MockEnvironment env = production().withProperty("app.jwt.secret", "");

        assertThat(ExigencesProduction.manquements(env)).anyMatch(m -> m.contains("APP_JWT_SECRET"));
    }

    @Test
    void uneCleDeSignatureDejaPublieeEstRefusee() {
        assertThat(ExigencesProduction.manquements(production()
                .withProperty("app.jwt.secret", "SGCI-Secret-Key-Minimum-256-bits-For-HS256-Please-Change-In-Production")))
                .anyMatch(m -> m.contains("APP_JWT_SECRET"));
        assertThat(ExigencesProduction.manquements(production()
                .withProperty("app.jwt.secret", "X7mD!r8KpE6@Z5YVQ#s1A4dFJ0Uo$B9%2H^tWCLN*eG")))
                .anyMatch(m -> m.contains("APP_JWT_SECRET"));
    }

    @Test
    void uneCleDeSignatureTropCourteEstRefusee() {
        assertThat(ExigencesProduction.manquements(production().withProperty("app.jwt.secret", "court")))
                .anyMatch(m -> m.contains("256 bits"));
    }

    @Test
    void unMotDePasseDeBaseVideOuPublieEstRefuse() {
        assertThat(ExigencesProduction.manquements(production().withProperty("spring.datasource.password", "")))
                .anyMatch(m -> m.contains("SPRING_DATASOURCE_PASSWORD"));
        assertThat(ExigencesProduction.manquements(production().withProperty("spring.datasource.password", "Admin@1234")))
                .anyMatch(m -> m.contains("SPRING_DATASOURCE_PASSWORD"));
        // Publié à son tour dans le dépôt par le commit 29e9a32 (2026-10-27).
        assertThat(ExigencesProduction.manquements(production().withProperty("spring.datasource.password", "RootPassword@2026")))
                .anyMatch(m -> m.contains("SPRING_DATASOURCE_PASSWORD"));
    }

    @Test
    void lesIdentifiantsMinioParDefautSontRefuses() {
        assertThat(ExigencesProduction.manquements(production()
                .withProperty("minio.access-key", "admin")
                .withProperty("minio.secret-key", "password123")))
                .anyMatch(m -> m.contains("MINIO_ACCESS_KEY"))
                .anyMatch(m -> m.contains("MINIO_SECRET_KEY"));
    }

    @Test
    void sansMinioSesIdentifiantsNeSontPasExiges() {
        MockEnvironment env = production()
                .withProperty("app.storage.backend", "local")
                .withProperty("minio.access-key", "")
                .withProperty("minio.secret-key", "");

        assertThat(ExigencesProduction.manquements(env)).isEmpty();
    }

    @Test
    void lesManquementsNeCitentJamaisLaValeurDuSecret() {
        assertThat(ExigencesProduction.manquements(production().withProperty("spring.datasource.password", "Admin@1234")))
                .noneMatch(m -> m.contains("Admin@1234"));
    }

    @Test
    void horsProductionRienNEstExige() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("local");

        assertThat(ExigencesProduction.manquements(dev)).isEmpty();
    }

    // ── la clé de signature au démarrage ───────────────────────────────────────────────────────

    @Test
    void enDeveloppementSansCleUneCleDeSessionEstGeneree() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("local");
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("");

        new SecuriteAuDemarrage(dev, jwt).afterPropertiesSet();

        assertThat(jwt.getSecret()).isNotBlank();
        assertThat(jwt.getSecret().getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
    }

    @Test
    void enProductionUnSecretManquantArreteLeDemarrage() {
        MockEnvironment env = production().withProperty("app.jwt.secret", "");
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("");

        assertThatThrownBy(() -> new SecuriteAuDemarrage(env, jwt).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    void laCleNestPlusEcriteDansLeCode() {
        assertThat(new JwtProperties().getSecret()).isNull();
    }

    private static MockEnvironment production() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.secret", SECRET_SOLIDE)
                .withProperty("spring.datasource.password", "Mdp-Base-Production-2026!")
                .withProperty("app.storage.backend", "minio")
                .withProperty("minio.access-key", "sgci-prod")
                .withProperty("minio.secret-key", "Cle-Minio-Production-2026!");
        env.setActiveProfiles("prod");
        return env;
    }
}
