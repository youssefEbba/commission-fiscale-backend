package mr.gov.finances.sgci.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.RolePermission;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public List<Permission> findAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Set<String> findPermissionCodesByRole(Role role) {
        return rolePermissionRepository.findByRole(role)
                .stream()
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Permission> findPermissionsByRole(Role role) {
        return rolePermissionRepository.findByRole(role)
                .stream()
                .map(RolePermission::getPermission)
                .distinct()
                .toList();
    }

    /**
     * Union des permissions {@link Role#AUTORITE_CONTRACTANTE}, {@link Role#AUTORITE_UPM} et
     * {@link Role#AUTORITE_UEP} — utilisé pour l’impersonation « autorité » par la commission relais
     * afin d’aligner le jeton sur les capacités réelles des profils AC (souvent UPM ou UEP en production).
     */
    @Transactional(readOnly = true)
    public List<String> findPermissionCodesUnionAutoriteContractanteProfiles() {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.addAll(findPermissionCodesByRole(Role.AUTORITE_CONTRACTANTE));
        codes.addAll(findPermissionCodesByRole(Role.AUTORITE_UPM));
        codes.addAll(findPermissionCodesByRole(Role.AUTORITE_UEP));
        return new ArrayList<>(codes);
    }

    @Transactional
    public void assignPermission(Role role, String permissionCode) {
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission inconnue: " + permissionCode));
        rolePermissionRepository.findByRoleAndPermission(role, permission)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Permission déjà assignée à ce rôle");
                });
        rolePermissionRepository.save(RolePermission.builder()
                .role(role)
                .permission(permission)
                .build());
    }

    @Transactional
    public void revokePermission(Role role, String permissionCode) {
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission inconnue: " + permissionCode));
        rolePermissionRepository.deleteByRoleAndPermission(role, permission);
    }
}
