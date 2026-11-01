package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les origines autorisées à appeler l'API depuis un navigateur, selon le profil.
 *
 * <p>Le 2026-10-27, la connexion en production répondait 403 « Invalid CORS request » : le front sur
 * {@code https://commission-fiscale.gov.mr} n'était pas dans la liste. Derrière nginx, l'application
 * voit une requête http, et l'origine https lui paraît donc étrangère : la liste est consultée.
 *
 * <p>Le 2026-10-31, la production a été restreinte à ce seul domaine. Hors production, les aperçus
 * Lovable et le front local restent autorisés : sans eux, on ne peut ni développer ni prévisualiser.
 */
class CorsAllowedOriginsTest {

    private static final String PRODUCTION = "https://commission-fiscale.gov.mr";
    private static final String APERCU_LOVABLE = "https://id-preview--e6e2ee9f-7068-4371-95aa-8f4613a60836.lovable.app";
    private static final String FRONT_LOCAL = "http://localhost:5173";

    @Test
    void enProductionSeulLeDomaineOfficielEstAutorise() {
        MockEnvironment prod = profil("prod");

        assertThat(CorsAllowedOrigins.autorisee(prod, PRODUCTION)).isTrue();
        assertThat(CorsAllowedOrigins.autorisee(prod, APERCU_LOVABLE)).isFalse();
        assertThat(CorsAllowedOrigins.autorisee(prod, FRONT_LOCAL)).isFalse();
    }

    @Test
    void horsProductionLesApercusEtLeFrontLocalSontAutorises() {
        MockEnvironment dev = profil("local");

        assertThat(CorsAllowedOrigins.autorisee(dev, PRODUCTION)).isTrue();
        assertThat(CorsAllowedOrigins.autorisee(dev, APERCU_LOVABLE)).isTrue();
        assertThat(CorsAllowedOrigins.autorisee(dev, FRONT_LOCAL)).isTrue();
    }

    @Test
    void laConfigurationCorsAccepteLaConnexionDepuisLaProduction() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(CorsAllowedOrigins.pour(profil("prod")));
        config.setAllowCredentials(true);

        assertThat(config.checkOrigin(PRODUCTION)).isEqualTo(PRODUCTION);
    }

    @Test
    void uneOrigineInconnueResteRefusee() {
        assertThat(CorsAllowedOrigins.autorisee(profil("prod"), "https://exemple-malveillant.test")).isFalse();
        assertThat(CorsAllowedOrigins.autorisee(profil("local"), "https://exemple-malveillant.test")).isFalse();
        assertThat(CorsAllowedOrigins.autorisee(profil("local"), null)).isFalse();
    }

    private static MockEnvironment profil(String nom) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(nom);
        return env;
    }
}
