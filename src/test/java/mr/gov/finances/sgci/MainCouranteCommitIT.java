package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La main courante survit au commit, pour chaque role.
 *
 * <p><b>Classe separee, et sans aucune fixture : les deux tiennent a la meme raison.</b>
 *
 * <p>Ce test ne peut pas porter {@code @Transactional}, sinon il ne verifie rien. Tous les autres
 * tests de la main courante en portent : leur transaction est annulee a la fin, aucun commit n'a
 * lieu, et une transaction marquee « rollback-only » ne se manifeste jamais. En production le
 * commit a bien lieu, et Spring leve alors {@code UnexpectedRollbackException}, rendue a l'ecran
 * en 400. C'est exactement le defaut qui a casse la main courante pour tous les roles sauf la
 * DGTCP, et qu'aucun des dix tests transactionnels ne voyait.
 *
 * <p>Corollaire : sans transaction de test, tout ce qu'un {@code @BeforeEach} ecrirait serait
 * <b>committe</b> et resterait en base pour les classes suivantes. D'ou l'absence de fixture : ce
 * test ne lit que les comptes du jeu de demonstration, et n'ecrit rien.
 */
@SpringBootTest
@ActiveProfiles("test")
class MainCouranteCommitIT {

    @Autowired
    private MainCouranteService service;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    void laMainCouranteSurvitAuCommitPourChaqueRole() {
        for (Role role : Role.values()) {
            Utilisateur u = utilisateurRepository.findAll().stream()
                    .filter(x -> x.getRole() == role)
                    .findFirst()
                    .orElse(null);
            if (u == null) {
                continue;
            }
            AuthenticatedUser acteur = new AuthenticatedUser(u.getId(), u.getUsername(), role);
            assertThatCode(() -> service.pourMoi(acteur))
                    .as("main courante du role %s, transaction reellement validee", role)
                    .doesNotThrowAnyException();
        }
    }
}
