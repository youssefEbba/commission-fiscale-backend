package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AuthService;
import mr.gov.finances.sgci.service.UtilisateurService;
import mr.gov.finances.sgci.web.dto.ChangeMyPasswordRequest;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une session fermée l'est vraiment.
 *
 * <p>Constat de la revue de sécurité : un jeton restait valable 24 heures quoi qu'il arrive. Se
 * déconnecter ne faisait qu'oublier le jeton dans le navigateur ; changer son mot de passe — par
 * exemple après un vol de session — laissait le voleur connecté jusqu'à l'expiration.
 *
 * <p>Par MockMvc, pour traverser le vrai filtre JWT. Chaque test crée son propre compte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionsIT {

    private static final String MDP = "Mot-De-Passe-Session-2026";

    @Autowired private MockMvc mvc;
    @Autowired private AuthService authService;
    @Autowired private UtilisateurService utilisateurService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void apresLaDeconnexionLeJetonEstRefuse() throws Exception {
        String jeton = connexion(nouveauCompte());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jeton)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jeton)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jeton)).andExpect(status().isUnauthorized());
    }

    @Test
    void uneNouvelleConnexionApresDeconnexionFonctionne() throws Exception {
        String compte = nouveauCompte();
        String ancien = connexion(compte);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + ancien)).andExpect(status().isNoContent());

        String nouveau = connexion(compte);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nouveau)).andExpect(status().isOk());
    }

    @Test
    void changerSonMotDePasseFermeLesAutresSessions() throws Exception {
        String compte = nouveauCompte();
        String volee = connexion(compte);
        Utilisateur u = utilisateurRepository.findByUsername(compte).orElseThrow();

        ChangeMyPasswordRequest r = new ChangeMyPasswordRequest();
        r.setCurrentPassword(MDP);
        r.setNewPassword("Nouveau-Mot-De-Passe-2026");
        utilisateurService.changeMyPassword(new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole()), r);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + volee)).andExpect(status().isUnauthorized());
    }

    private String nouveauCompte() {
        String username = "session-" + System.nanoTime();
        utilisateurRepository.saveAndFlush(Utilisateur.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(MDP))
                .role(Role.DGB)
                .nomComplet("Compte de test session")
                .email(username + "@exemple.mr")
                .actif(true)
                .build());
        return username;
    }

    private String connexion(String username) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(MDP);
        return authService.login(r).getToken();
    }
}
