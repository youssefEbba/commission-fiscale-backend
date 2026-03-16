package mr.gov.finances.sgci.repository;

import java.util.List;
import java.util.Optional;

import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.RolePermission;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(Role role);
    Optional<RolePermission> findByRoleAndPermission(Role role, Permission permission);
    void deleteByRoleAndPermission(Role role, Permission permission);
}
