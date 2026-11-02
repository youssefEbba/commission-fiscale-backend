package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.AuthService;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La DGTCP peut enregistrer les montants d'une mise en place, quelle que soit la méthode du front.
 *
 * <p>Constaté en recette : le front publié envoie POST sur {@code /montants}, route qui n'acceptait
 * que PATCH. La réponse était une « Erreur interne » et la DGTCP ne pouvait plus poser son visa.
 *
 * <p>Un certificat inexistant suffit : un 404 prouve que l'appel a atteint le service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MontantsMiseEnPlaceIT {

    private static final String MDP = "Mot-De-Passe-Montants-2026";
    private static final String ROUTE = "/api/certificats-credit/999999999/montants";
    private static final String CORPS = "{\"montantCordon\":2500000,\"montantTVAInterieure\":3800000}";

    @Autowired private MockMvc mvc;
    @Autowired private AuthService authService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void lesMontantsSontAcceptesEnPost() throws Exception {
        mvc.perform(post(ROUTE).header("Authorization", "Bearer " + jetonDgtcp())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isNotFound());
    }

    @Test
    void lesMontantsRestentAcceptesEnPatch() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", "Bearer " + jetonDgtcp())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneMethodeNonAccepteeRend405EtNon500() throws Exception {
        mvc.perform(put(ROUTE).header("Authorization", "Bearer " + jetonDgtcp())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(containsString("PATCH")))
                .andExpect(header().string("Allow", containsString("POST")));
    }

    private String jetonDgtcp() {
        String username = "montants-" + System.nanoTime();
        utilisateurRepository.saveAndFlush(Utilisateur.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(MDP))
                .role(Role.DGTCP)
                .nomComplet("Compte de test montants")
                .email(username + "@exemple.mr")
                .actif(true)
                .build());
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(MDP);
        return authService.login(r).getToken();
    }
}
