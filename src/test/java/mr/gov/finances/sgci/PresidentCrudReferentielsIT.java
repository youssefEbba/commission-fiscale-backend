package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.AutoriteContractanteController;
import mr.gov.finances.sgci.web.controller.EntrepriseController;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le Président peut-il réellement administrer les autorités contractantes et les entreprises ?
 *
 * <p>Question posée par l'équipe. La réponse est oui, mais elle ne se lit pas d'un coup d'œil :
 * elle dépend de trois mécanismes qui pourraient chacun la contredire, et dont aucun n'est visible
 * depuis le contrôleur.
 *
 * <ul>
 *   <li>Les six routes sont gardées par des <b>permissions</b>, pas par des rôles — bonne nouvelle,
 *       mais une permission référencée dans une annotation et jamais déclarée au seed bloquerait
 *       tout le monde, silencieusement.</li>
 *   <li>Le Président les reçoit par une <b>attribution globale</b> et non par une liste explicite :
 *       rien dans le seed ne nomme {@code autorite.create} à côté de son rôle.</li>
 *   <li>Une <b>migration de révocation</b> retire des permissions à chaque démarrage. Elle ne vise
 *       aujourd'hui que l'autorité contractante et la DGI, mais une ligne ajoutée par erreur au
 *       mauvais rôle désarmerait le Président sans qu'aucun test ne s'en aperçoive.</li>
 * </ul>
 *
 * <p>Ce test exerce donc les six opérations plutôt que de relire les annotations, et il vérifie
 * aussi qu'un rôle qui ne doit pas administrer est bien refusé — sans quoi un
 * {@code isAuthenticated()} posé par mégarde passerait pour un succès.
 *
 * <p>Niveau service, avec authentification posée dans le contexte : Tomcat ne démarre pas sur ce
 * poste, et {@code @EnableMethodSecurity} suffit à faire évaluer les annotations.
 */
@SpringBootTest
@ActiveProfiles("test")
class PresidentCrudReferentielsIT {

    @Autowired
    private AutoriteContractanteController autoriteController;

    @Autowired
    private EntrepriseController entrepriseController;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    // ── les autorités contractantes ────────────────────────────────────────────────────────────

    @Test
    void lePresidentCreeLitModifieEtSupprimeUneAutorite() {
        authentifier("president", Role.PRESIDENT);
        String suffixe = String.valueOf(System.nanoTime());

        AutoriteContractanteDto creee = autoriteController.create(AutoriteContractanteDto.builder()
                .nom("Ministère de test " + suffixe)
                .code("AC-TEST-" + suffixe)
                .build());
        assertThat(creee.getId()).isNotNull();

        assertThat(autoriteController.getAll())
                .extracting(AutoriteContractanteDto::getId)
                .contains(creee.getId());
        assertThat(autoriteController.getById(creee.getId()).getNom())
                .isEqualTo("Ministère de test " + suffixe);

        creee.setNom("Ministère renommé " + suffixe);
        assertThat(autoriteController.update(creee.getId(), creee).getNom())
                .isEqualTo("Ministère renommé " + suffixe);

        Long id = creee.getId();
        assertThatCode(() -> autoriteController.delete(id)).doesNotThrowAnyException();
        assertThat(autoriteController.getAll())
                .extracting(AutoriteContractanteDto::getId)
                .doesNotContain(id);
    }

    // ── les entreprises ────────────────────────────────────────────────────────────────────────

    @Test
    void lePresidentCreeLitModifieEtSupprimeUneEntreprise() {
        authentifier("president", Role.PRESIDENT);
        String suffixe = String.valueOf(System.nanoTime());

        EntrepriseDto creee = entrepriseController.create(EntrepriseDto.builder()
                .raisonSociale("Entreprise de test " + suffixe)
                .nif("NIF-TEST-" + suffixe)
                .build());
        assertThat(creee.getId()).isNotNull();

        assertThat(entrepriseController.getAll())
                .extracting(EntrepriseDto::getId)
                .contains(creee.getId());
        // Le contrôle de périmètre court-circuite sur « entreprise.list » : sans cette permission,
        // le Président serait traité comme un déposant et refusé sur une entreprise qui n'est pas
        // la sienne — or il n'en a aucune.
        assertThat(entrepriseController.getById(creee.getId(), principalDe("president", Role.PRESIDENT))
                .getRaisonSociale()).isEqualTo("Entreprise de test " + suffixe);

        creee.setRaisonSociale("Entreprise renommée " + suffixe);
        assertThat(entrepriseController.update(creee.getId(), creee).getRaisonSociale())
                .isEqualTo("Entreprise renommée " + suffixe);

        Long id = creee.getId();
        assertThatCode(() -> entrepriseController.delete(id)).doesNotThrowAnyException();
        assertThat(entrepriseController.getAll())
                .extracting(EntrepriseDto::getId)
                .doesNotContain(id);
    }

    // ── les permissions derrière ───────────────────────────────────────────────────────────────

    @Test
    void lePresidentDetientBienLesSixPermissions() {
        // Assertion compagnonne des deux précédentes : si quelqu'un « réparait » un échec en
        // relâchant une annotation en isAuthenticated(), les tests ci-dessus passeraient encore.
        // Celui-ci dit que le droit existe vraiment, et nommément.
        List<String> permissions =
                new ArrayList<>(permissionService.findPermissionCodesByRole(Role.PRESIDENT));

        assertThat(permissions).contains(
                "autorite.create", "autorite.update", "autorite.delete",
                "entreprise.create", "entreprise.update", "entreprise.delete",
                "entreprise.list");
    }

    @Test
    void laDgiNAdministrePasLesEntreprises() {
        // La migration de révocation lui retire ce CRUD : avec l'attribution des rôles et des
        // permissions, elle pouvait s'accorder n'importe quel droit. Ce test est le garde-fou de
        // cette décision — et il prouve au passage que les annotations gardent vraiment la porte.
        assertThat(permissionService.findPermissionCodesByRole(Role.DGI))
                .doesNotContain("entreprise.create", "entreprise.update", "entreprise.delete");

        authentifier("dgi", Role.DGI);
        assertThatThrownBy(() -> entrepriseController.create(EntrepriseDto.builder()
                .raisonSociale("Tentative DGI")
                .build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uneEntrepriseNAdministrePasLeReferentiel() {
        authentifier("entreprise", Role.ENTREPRISE);

        assertThatThrownBy(() -> autoriteController.create(AutoriteContractanteDto.builder()
                .nom("Tentative entreprise")
                .build()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> entrepriseController.create(EntrepriseDto.builder()
                .raisonSociale("Tentative entreprise")
                .build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void laLectureDuReferentielEntrepriseEstPartageeParTousLesRoles() {
        // Décision délibérée du seed, pas un oubli : « seedReferentielReadPermissions » accorde
        // entreprise.list, groupement.list, convention.view.all, marche.view et projet.view.all à
        // tous les rôles, parce que les écrans ont besoin de choisir une entreprise ou un marché.
        //
        // Ce test existe pour que ce soit visible. Deux choses le distinguent des autres lectures
        // partagées, et méritent d'être connues : « findAll » n'applique aucun périmètre, et
        // EntrepriseDto porte le NIF, l'adresse et la situation fiscale. Un déposant lit donc ces
        // informations sur toutes les autres entreprises. Si cela doit changer, c'est ici qu'on
        // verra que la décision était consciente.
        for (Role role : List.of(Role.ENTREPRISE, Role.SOUS_TRAITANT, Role.AUTORITE_CONTRACTANTE)) {
            assertThat(permissionService.findPermissionCodesByRole(role))
                    .as("lecture du référentiel pour %s", role)
                    .contains("entreprise.list");
        }

        authentifier("entreprise", Role.ENTREPRISE);
        assertThatCode(() -> entrepriseController.getAll()).doesNotThrowAnyException();
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser principalDe(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), role);
    }

    private void authentifier(String username, Role role) {
        AuthenticatedUser principal = principalDe(username, role);
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionService.findPermissionCodesByRole(role)
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
