package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.ParametreApplicationRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ParametreService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.ParametreController;
import mr.gov.finances.sgci.web.controller.ParametrePublicController;
import mr.gov.finances.sgci.web.dto.ParametreDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le paramétrage : ce qui est lisible sans compte, et ce qui peut être écrit.
 *
 * <p>Deux tests portent l'essentiel, et tous deux protègent contre une erreur qui s'exposerait à
 * tout internet plutôt qu'à un utilisateur connecté.
 *
 * <ul>
 *   <li>{@link #laLecturePubliqueNeRendQueLaListeBlanche()} — la route publique ne doit jamais
 *       rendre la table. Le jour où un réglage sensible sera ajouté, c'est ce test qui dira qu'il
 *       ne fuite pas, sans que personne ait à y penser.</li>
 *   <li>{@link #uneCleInconnueEstRefuseeEtNonCreee()} — les réglages sont un référentiel fermé. Si
 *       une clé inconnue était créée à la volée, la table deviendrait un dépotoir et plus personne
 *       ne saurait ce qui est lu, par quel écran, ni si une clé sert encore.</li>
 * </ul>
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ParametrageIT {

    @Autowired
    private ParametreController controller;

    @Autowired
    private ParametrePublicController controllerPublic;

    @Autowired
    private ParametreApplicationRepository repository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    // ── la lecture publique ────────────────────────────────────────────────────────────────────

    @Test
    void laLecturePubliqueNeRendQueLaListeBlanche() {
        // Sans authentification : c'est la page de connexion qui appelle cette route.
        SecurityContextHolder.clearContext();

        Map<String, String> publiques = controllerPublic.lirePubliques();

        assertThat(publiques).containsKeys(
                ParametreService.CONTACT_ADRESSE,
                ParametreService.CONTACT_EMAIL,
                ParametreService.CONTACT_TELEPHONE);
        // Et rien de plus : toute clé ajoutée au référentiel est privée par défaut.
        assertThat(publiques).hasSize(3);
        assertThat(publiques.keySet()).allSatisfy(cle ->
                assertThat(cle).startsWith("contact."));
    }

    @Test
    void lesValeursEnVigueurSontCellesDeLAffichageActuel() {
        // Semées avec les valeurs du pied de page : la mise en service ne doit rien changer à
        // l'écran. Un paramétrage qui modifie l'affichage le jour de sa livraison se fait remarquer
        // pour la mauvaise raison.
        Map<String, String> publiques = controllerPublic.lirePubliques();

        assertThat(publiques.get(ParametreService.CONTACT_ADRESSE)).isEqualTo("Nouakchott, Mauritanie");
        assertThat(publiques.get(ParametreService.CONTACT_EMAIL))
                .isEqualTo("contact@commission-fiscale.gov.mr");
        assertThat(publiques.get(ParametreService.CONTACT_TELEPHONE)).isEqualTo("+222 48 69 89 83");
    }

    // ── l'écriture ─────────────────────────────────────────────────────────────────────────────

    @Test
    void lAdministrateurModifieUnReglageEtLaLecturePubliqueSuit() {
        authentifier("admin", Role.ADMIN_SI);
        String avant = repository.findByCle(ParametreService.CONTACT_TELEPHONE).orElseThrow().getValeur();
        try {
            ParametreDto modifie = controller.modifier(
                    ParametreService.CONTACT_TELEPHONE, requete("+222 11 22 33 44"));

            assertThat(modifie.getValeur()).isEqualTo("+222 11 22 33 44");
            assertThat(modifie.isPublique()).isTrue();
            assertThat(modifie.getDateModification()).isNotNull();

            SecurityContextHolder.clearContext();
            assertThat(controllerPublic.lirePubliques())
                    .containsEntry(ParametreService.CONTACT_TELEPHONE, "+222 11 22 33 44");
        } finally {
            // Le réglage est partagé par toute la base de test : on remet la valeur d'origine,
            // sinon le test suivant qui lit les valeurs en vigueur échouerait selon l'ordre.
            authentifier("admin", Role.ADMIN_SI);
            controller.modifier(ParametreService.CONTACT_TELEPHONE, requete(avant));
        }
    }

    @Test
    void uneCleInconnueEstRefuseeEtNonCreee() {
        authentifier("admin", Role.ADMIN_SI);
        long avant = repository.count();

        assertThatThrownBy(() -> controller.modifier("contact.whatsapp", requete("+222 00 00 00 00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("référentiel fermé");

        assertThat(repository.count()).isEqualTo(avant);
        assertThat(repository.existsByCle("contact.whatsapp")).isFalse();
    }

    // ── l'habilitation ─────────────────────────────────────────────────────────────────────────

    @Test
    void lePresidentPeutAussiParametrer() {
        // Il reçoit la permission par l'attribution globale, comme pour les signatures.
        assertThat(permissionService.findPermissionCodesByRole(Role.PRESIDENT))
                .contains("parametre.manage");

        authentifier("president", Role.PRESIDENT);
        assertThat(controller.lireToutes()).isNotEmpty();
    }

    @Test
    void uneEntrepriseNeParametrePas() {
        assertThat(permissionService.findPermissionCodesByRole(Role.ENTREPRISE))
                .doesNotContain("parametre.manage");

        authentifier("entreprise", Role.ENTREPRISE);
        assertThatThrownBy(() -> controller.lireToutes())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.modifier(
                ParametreService.CONTACT_EMAIL, requete("pirate@example.com")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void laListeAdministrativeDitCeQuiEstPublic() {
        // L'agent qui modifie doit savoir ce qu'il rend visible : les coordonnées apparaissent sur
        // la page de connexion, donc avant toute authentification.
        authentifier("admin", Role.ADMIN_SI);

        assertThat(controller.lireToutes())
                .allSatisfy(p -> assertThat(p.getDescription()).isNotBlank())
                .anySatisfy(p -> assertThat(p.isPublique()).isTrue());
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private ParametreController.ModifierParametreRequest requete(String valeur) {
        ParametreController.ModifierParametreRequest r = new ParametreController.ModifierParametreRequest();
        r.setValeur(valeur);
        return r;
    }

    private void authentifier(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getUsername(), role);
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionService.findPermissionCodesByRole(role)
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
