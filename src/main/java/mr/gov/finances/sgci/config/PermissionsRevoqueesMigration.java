package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Retire les droits que le seed accordait à tort, sur les bases déjà en service.
 *
 * <p>{@code DataInitializer.assign()} est purement additif : il crée la liaison rôle↔permission
 * manquante et n'enlève jamais rien. Retirer une permission de la liste du seed suffit donc pour une
 * base neuve, mais ne change rien là où elle a déjà été accordée — d'où cette migration.
 *
 * <p>Deux révocations, décidées en réunion de recette :
 * <ul>
 *   <li>{@code AUTORITE_CONTRACTANTE} perd {@code delegue.create} : rattacher une personne à une
 *       entité relève désormais du Président, par demande validée. L'AC garde la lecture, la mise à
 *       jour des coordonnées et la désactivation immédiate d'un agent qui part — couper un accès
 *       n'est pas rattacher, et faire remonter cela au Président serait une perte de sécurité.</li>
 *   <li>{@code DGI} perd l'administration des comptes, des rôles et des permissions, ainsi que le
 *       CRUD entreprise. Avec {@code user.role.assign} et {@code permissions.manage}, elle pouvait
 *       s'attribuer n'importe quel droit.</li>
 * </ul>
 *
 * <p><strong>Cette migration s'exécute à chaque démarrage</strong>, et c'est délibéré : elle ne
 * dispose d'aucun marqueur pour savoir si une liaison absente l'est parce qu'elle l'a retirée, ou
 * parce que personne ne l'a jamais accordée. Elle rétablit donc la décision à chaque boot. La
 * conséquence à connaître : une permission de cette liste réaccordée depuis l'écran des rôles sera
 * reprise au redémarrage suivant. Pour l'accorder durablement, il faut retirer son entrée d'ici —
 * ce qui oblige à décider en conscience plutôt que par un clic.
 *
 * <p>{@code @Order(110)} : après {@code DataInitializer} ({@code @Order(100)}), sans quoi le seed
 * réaccorderait aussitôt ce que la migration vient de retirer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(110)
public class PermissionsRevoqueesMigration implements ApplicationRunner {

    /** Les liaisons à retirer, par rôle. */
    private static final Map<Role, List<String>> A_REVOQUER = Map.of(
            Role.AUTORITE_CONTRACTANTE, List.of(
                    "delegue.create"),
            Role.DGI, List.of(
                    "user.create",
                    "user.update",
                    "user.disable",
                    "user.reset",
                    "user.list",
                    "user.role.assign",
                    "role.create",
                    "role.permissions.update",
                    "role.list",
                    "role.disable",
                    "permissions.manage",
                    "entreprise.create",
                    "entreprise.update",
                    "entreprise.delete"));

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int retirees = 0;
        for (Map.Entry<Role, List<String>> entry : A_REVOQUER.entrySet()) {
            Role role = entry.getKey();
            for (String code : entry.getValue()) {
                Optional<Permission> permission = permissionRepository.findByCode(code);
                if (permission.isEmpty()) {
                    continue;
                }
                if (rolePermissionRepository.findByRoleAndPermission(role, permission.get()).isPresent()) {
                    rolePermissionRepository.deleteByRoleAndPermission(role, permission.get());
                    log.info("Permission {} retirée au rôle {}", code, role);
                    retirees++;
                }
            }
        }
        if (retirees > 0) {
            log.info("Migration permissions : {} liaison(s) rôle/permission retirée(s)", retirees);
        }
    }
}
