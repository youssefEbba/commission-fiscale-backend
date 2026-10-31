package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le domaine de production est une origine autorisée.
 *
 * <p>Le 2026-10-27, la connexion en production répondait 403 « Invalid CORS request » : le front sur
 * {@code https://commission-fiscale.gov.mr} n'était pas dans la liste. Derrière nginx, l'application
 * voit une requête http, et l'origine https lui paraît donc étrangère : la liste est consultée.
 */
class CorsAllowedOriginsTest {

    private static final String PRODUCTION = "https://commission-fiscale.gov.mr";

    @Test
    void leDomaineDeProductionEstAutorise() {
        assertThat(CorsAllowedOrigins.matchesFrontendOrigin(PRODUCTION)).isTrue();
    }

    @Test
    void laConfigurationCorsAccepteLaConnexionDepuisLaProduction() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(CorsAllowedOrigins.FRONTEND);
        config.setAllowCredentials(true);

        assertThat(config.checkOrigin(PRODUCTION)).isEqualTo(PRODUCTION);
    }

    @Test
    void uneOrigineInconnueResteRefusee() {
        assertThat(CorsAllowedOrigins.matchesFrontendOrigin("https://exemple-malveillant.test")).isFalse();
    }
}
