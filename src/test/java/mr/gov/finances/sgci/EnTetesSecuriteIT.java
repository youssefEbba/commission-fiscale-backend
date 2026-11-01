package mr.gov.finances.sgci;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Chaque réponse porte les en-têtes de sécurité que le navigateur applique.
 *
 * <p>Constat de la revue de sécurité : seul {@code X-Frame-Options: SAMEORIGIN} était posé, pour la
 * console H2. Ni HSTS (le navigateur reste en HTTPS), ni de politique de référent.
 *
 * <p>MockMvc ne démarre pas Tomcat : il passe sur ce poste, contrairement aux tests web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnTetesSecuriteIT {

    @Autowired private MockMvc mvc;

    @Test
    void lesReponsesPortentLesEnTetesDeSecurite() throws Exception {
        mvc.perform(get("/api/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void laConsoleH2NEstPlusOuverte() throws Exception {
        mvc.perform(get("/h2-console/"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .as("la console H2 ne doit pas être accessible sans authentification")
                        .isIn(401, 403, 404));
    }
}
