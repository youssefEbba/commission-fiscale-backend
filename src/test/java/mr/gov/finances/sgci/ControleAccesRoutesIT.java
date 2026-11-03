package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.controller.AuditLogController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaque route déclare qui peut l'appeler.
 *
 * <p>Défaut constaté à la revue de sécurité : {@code GET /api/audit-logs} ne portait aucun
 * {@code @PreAuthorize}. N'importe quel compte connecté, une entreprise comprise, lisait le journal
 * d'audit entier — avec l'instantané des objets enregistrés. La permission
 * {@code security.audit.view} existait, mais rien ne la vérifiait.
 *
 * <p>L'inventaire empêche le défaut de revenir sur une autre route : une route sans
 * {@code @PreAuthorize} doit figurer dans la liste ci-dessous, avec sa raison.
 *
 * <p>Les contrôles vivent en {@code @PreAuthorize} et les tests web ne démarrent pas sur ce poste :
 * {@code @EnableMethodSecurity} étant actif, un appel direct au bean du contrôleur est intercepté
 * dès qu'une authentification est posée.
 */
@SpringBootTest
@ActiveProfiles("test")
class ControleAccesRoutesIT {

    /** Routes sans {@code @PreAuthorize}, chacune pour une raison écrite. */
    private static final Set<String> SANS_ANNOTATION = Set.of(
            // Publiques par nature : se connecter, demander un nouveau mot de passe, vérifier un
            // certificat, déposer une demande de rattachement, lire les paramètres publics.
            "AuthController.login",
            "AuthController.requestPasswordReset",
            "AuthController.checkEmailForPasswordReset",
            "HealthController.health",
            "ParametrePublicController.lirePubliques",
            "RattachementPublicController.deposer",
            "RattachementPublicController.entites",
            "RattachementPublicController.pieces",
            "RattachementPublicController.suivi",
            "VerificationPubliqueController.verifier",
            // Le compte connecté sur lui-même : aucune donnée d'autrui.
            "AuthController.me",
            "AuthController.logout",
            "AuthController.currentPermissions",
            "UtilisateurController.getMyProfile",
            "UtilisateurController.updateMyProfile",
            "UtilisateurController.changeMyPassword",
            "MainCouranteController.pourMoi",
            // Le contrôle porte sur la pièce elle-même (rôle, dossier, visibilité) : il est dans le code.
            "DocumentDownloadController.download",
            // Le jeton d'un relais en impersonation ne porte que les droits de l'entité incarnée :
            // CommissionRelaisService vérifie le rôle du compte EN BASE, à chaque route.
            "CommissionRelaisController.listEntreprises",
            "CommissionRelaisController.listAutorites",
            "CommissionRelaisController.impersonateEntreprise",
            "CommissionRelaisController.impersonateAutorite",
            "CommissionRelaisController.release");

    @Autowired private ApplicationContext context;
    @Autowired private AuditLogController auditLogController;
    @Autowired private PermissionService permissionService;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chaqueRouteDeclareQuiPeutLAppeler() {
        Set<String> sansControle = new TreeSet<>();
        for (Object bean : context.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            if (!type.getPackageName().startsWith("mr.gov.finances.sgci")) {
                // Swagger (springdoc) : fermé en production par configuration, pas par annotation.
                continue;
            }
            boolean surLaClasse = AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class);
            for (Method m : type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)) {
                    continue;
                }
                String nom = type.getSimpleName() + "." + m.getName();
                if (!surLaClasse && !AnnotatedElementUtils.hasAnnotation(m, PreAuthorize.class)
                        && !SANS_ANNOTATION.contains(nom)) {
                    sansControle.add(nom);
                }
            }
        }
        assertThat(sansControle)
                .as("route sans @PreAuthorize : ajouter le contrôle, ou l'inscrire avec sa raison")
                .isEmpty();
    }

    /**
     * {@code @CrossOrigin(origins = "*")} sur un contrôleur passe outre la liste d'origines de
     * {@code CorsAllowedOrigins} : n'importe quel site pouvait appeler l'API depuis le navigateur
     * d'un utilisateur connecté.
     */
    @Test
    void aucunControleurNOuvreLAPIATouteOrigine() {
        Set<String> ouverts = new TreeSet<>();
        for (Object bean : context.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            if (AnnotatedElementUtils.hasAnnotation(type, org.springframework.web.bind.annotation.CrossOrigin.class)) {
                ouverts.add(type.getSimpleName());
            }
        }
        assertThat(ouverts).as("les origines autorisées sont réglées une fois, dans SecurityConfig").isEmpty();
    }

    @Test
    void uneEntrepriseNeLitPasLeJournalDAudit() {
        authentifier("entreprise", Role.ENTREPRISE);

        assertThatThrownBy(() -> auditLogController.getAll(null, null, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void lAdministrateurLitLeJournalDAudit() {
        authentifier("admin", Role.ADMIN_SI);

        assertThatCode(() -> auditLogController.getAll(null, null, null, null, null, 0, 20))
                .doesNotThrowAnyException();
    }

    /** Pose dans le contexte de sécurité une authentification identique à celle du filtre JWT. */
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
