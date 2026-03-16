package mr.gov.finances.sgci.service;

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
