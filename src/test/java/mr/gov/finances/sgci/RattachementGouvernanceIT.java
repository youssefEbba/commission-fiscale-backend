package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ComptesEntiteService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.AutoriteContractanteController;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qui a le droit de créer une entité, et qui rattache les personnes.
 *
 * <p>Les contrôles d'accès sont posés en {@code @PreAuthorize}, donc invérifiables par un test de
 * service — et les tests web ne démarrent pas sur ce poste. Comme {@code @EnableMethodSecurity} est
 * actif, un appel direct au bean du contrôleur <em>est</em> intercepté : il suffit de poser une
 * authentification dans le contexte de sécurité. C'est ce que fait ce test, et c'est la seule façon
 * de prouver ici que le CRUD des autorités contractantes est enfin fermé.
 *
 * <p>{@link #lesDroitsDadministrationDeLaDgiSontRetires()} vérifie l'autre moitié du lot : le seed
 * n'accorde plus ces droits, et la migration retire ceux déjà accordés.
 */
@SpringBootTest
@ActiveProfiles("test")
class RattachementGouvernanceIT {

    @Autowired
    private AutoriteContractanteController autoriteController;

    @Autowired
    private ComptesEntiteService comptesService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    // ── le CRUD des autorités contractantes est fermé ──────────────────────────────────────────

    @Test
    void uneAutoriteContractanteNePeutPlusEnCreerUne() {
        authentifier("ac", Role.AUTORITE_CONTRACTANTE);

        assertThatThrownBy(() -> autoriteController.create(nouvelleAutorite()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uneAutoriteContractanteNePeutPlusEnSupprimerUne() {
        authentifier("ac", Role.AUTORITE_CONTRACTANTE);

        assertThatThrownBy(() -> autoriteController.delete(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void lePresidentPeutCreerUneAutoriteContractante() {
        authentifier("president", Role.PRESIDENT);

        // L'appel traverse le @PreAuthorize : s'il échouait sur l'autorisation, on verrait un
        // AccessDeniedException et non une éventuelle erreur métier.
        assertThatCode(() -> autoriteController.create(nouvelleAutorite()))
                .doesNotThrowAnyException();
    }

    // ── les révocations ────────────────────────────────────────────────────────────────────────

    @Test
    void lautoriteContractanteNePeutPlusCreerDeDelegue() {
        // Le rattachement d'une personne relève du Président. L'AC garde la lecture, la mise à jour
        // des coordonnées et la désactivation d'un agent qui part.
        assertThat(permissionsDe(Role.AUTORITE_CONTRACTANTE)).doesNotContain("delegue.create");
        assertThat(permissionsDe(Role.AUTORITE_CONTRACTANTE))
                .contains("delegue.list", "delegue.update", "delegue.disable");
    }

    @Test
    void lautoriteContractanteGardeLaCreationDentrepriseAuReferentiel() {
        // 286 entreprises pour 2 comptes : les AC en créent au fil des dossiers, et une demande de
        // correction exige un entrepriseId existant. Le leur retirer arrêterait l'instruction.
        assertThat(permissionsDe(Role.AUTORITE_CONTRACTANTE))
                .contains("entreprise.create", "entreprise.update");
    }

    @Test
    void lesDroitsDadministrationDeLaDgiSontRetires() {
        List<String> dgi = permissionsDe(Role.DGI);

        assertThat(dgi).doesNotContain(
                "user.create", "user.update", "user.disable", "user.reset", "user.list",
                "user.role.assign", "role.create", "role.permissions.update", "role.disable",
                "permissions.manage", "entreprise.create", "entreprise.update", "entreprise.delete");
        // Le métier et la consultation restent : la DGI vise des dossiers, elle n'administre pas.
        assertThat(dgi).contains("entreprise.list", "correction.dgi.visa");
    }

    @Test
    void laRevocationEstIdempotente() {
        // La migration s'exécute à chaque démarrage : un second passage sur une base déjà nettoyée
        // ne doit rien trouver ni rien casser.
        Optional<Permission> delegueCreate = permissionRepository.findByCode("delegue.create");
        assertThat(delegueCreate).isPresent();
        assertThat(rolePermissionRepository
                .findByRoleAndPermission(Role.AUTORITE_CONTRACTANTE, delegueCreate.get()))
                .isEmpty();
    }

    @Test
    void lePresidentDetientLaPermissionDeRattachement() {
        assertThat(permissionsDe(Role.PRESIDENT)).contains("rattachement.validate");
        assertThat(permissionsDe(Role.AUTORITE_CONTRACTANTE)).doesNotContain("rattachement.validate");
    }

    // ── les comptes d'une entité ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesComptesDuneEntiteSontListes() {
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        Long entrepriseId = compteEntreprise.getEntreprise().getId();

        List<UtilisateurDto> comptes =
                comptesService.lister(TypeEntiteRattachement.ENTREPRISE, entrepriseId);

        assertThat(comptes).extracting(UtilisateurDto::getUsername).contains("entreprise");
    }

    @Test
    @Transactional
    void onNactivePasUnCompteDepuisLaFicheDuneAutreEntite() {
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        Entreprise autre = entrepriseRepository.findAll().stream()
                .filter(e -> !e.getId().equals(compteEntreprise.getEntreprise().getId()))
                .findFirst().orElseThrow();

        // Sans ce contrôle, la route deviendrait une activation universelle déguisée en action sur
        // une fiche d'entité.
        assertThatThrownBy(() -> comptesService.definirActif(
                TypeEntiteRattachement.ENTREPRISE, autre.getId(), compteEntreprise.getId(), false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("n'est pas rattaché à cette entité"));
    }

    @Test
    @Transactional
    void unRoleDeLaCommissionNePeutPasEtreRattacheAUneEntite() {
        Entreprise cible = entrepriseRepository.findAll().stream().findFirst().orElseThrow();

        assertThatThrownBy(() -> comptesService.creer(TypeEntiteRattachement.ENTREPRISE, cible.getId(),
                "compte-interdit-" + System.nanoTime(), "MotDePasse!2026", Role.PRESIDENT, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("non recevable"));
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private List<String> permissionsDe(Role role) {
        return new ArrayList<>(permissionService.findPermissionCodesByRole(role));
    }

    /** Pose dans le contexte de sécurité une authentification identique à celle du filtre JWT. */
    private void authentifier(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionService.findPermissionCodesByRole(role)
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getUsername(), role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private AutoriteContractanteDto nouvelleAutorite() {
        AutoriteContractanteDto dto = new AutoriteContractanteDto();
        dto.setNom("Autorité de test " + System.nanoTime());
        dto.setCode("ACT" + System.nanoTime());
        return dto;
    }
}
