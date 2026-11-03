package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.AuthService;
import mr.gov.finances.sgci.service.PasswordResetService;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La connexion ne se laisse ni deviner, ni interroger.
 *
 * <p>Constats de la revue de sécurité :
 * <ul>
 *   <li>aucune limite d'essais : un mot de passe pouvait être essayé sans fin ;</li>
 *   <li>« Compte en attente de validation » était répondu avant la vérification du mot de passe :
 *       n'importe qui apprenait qu'un identifiant existe ;</li>
 *   <li>{@code /api/auth/password-reset/check-email}, publique, répondait si une adresse a un
 *       compte : une liste d'adresses se vérifiait une à une.</li>
 * </ul>
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste. Chaque test crée son propre compte, pour
 * ne verrouiller aucun compte partagé avec les autres tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecuriteConnexionIT {

    private static final String BON = "Bon-Mot-De-Passe-2026";

    @Autowired private AuthService authService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void apresCinqEchecsLeCompteEstBloqueMemeAvecLeBonMotDePasse() {
        String compte = nouveauCompte(true);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(connexion(compte, "mauvais")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(connexion(compte, BON)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(429))
                .hasMessageContaining("Trop de tentatives");
    }

    @Test
    void uneConnexionReussieRemetLeCompteurAZero() {
        String compte = nouveauCompte(true);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(connexion(compte, "mauvais")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatCode(() -> authService.login(connexion(compte, BON))).doesNotThrowAnyException();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(connexion(compte, "mauvais")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatCode(() -> authService.login(connexion(compte, BON))).doesNotThrowAnyException();
    }

    @Test
    void unCompteEnAttenteNeSeDevoilePasSansSonMotDePasse() {
        String compte = nouveauCompte(false);

        assertThatThrownBy(() -> authService.login(connexion(compte, "mauvais")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiants incorrects");
        assertThatThrownBy(() -> authService.login(connexion("personne-" + System.nanoTime(), "mauvais")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiants incorrects");
    }

    @Test
    void laVerificationDAdresseNeDitPasSiUnCompteExiste() {
        Utilisateur existant = utilisateurRepository.findByUsername("entreprise").orElseThrow();

        assertThat(passwordResetService.checkEmail("inconnu-" + System.nanoTime() + "@exemple.mr").isExists())
                .isEqualTo(passwordResetService.checkEmail(existant.getEmail()).isExists());
    }

    private String nouveauCompte(boolean actif) {
        String username = "connexion-" + System.nanoTime();
        utilisateurRepository.saveAndFlush(Utilisateur.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(BON))
                .role(Role.DGB)
                .nomComplet("Compte de test connexion")
                .email(username + "@exemple.mr")
                .actif(actif)
                .build());
        return username;
    }

    private static LoginRequest connexion(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }
}
