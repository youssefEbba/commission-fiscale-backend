package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.dto.PermissionDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('permissions.manage')")
public class PermissionAdminController {

    private final PermissionService permissionService;

    @GetMapping
    public List<PermissionDto> listPermissions() {
        return permissionService.findAllPermissions()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/roles")
    public List<Role> listRoles() {
        return Arrays.asList(Role.values());
    }

    @GetMapping("/roles/{role}")
    public List<PermissionDto> getPermissionsByRole(@PathVariable Role role) {
        return permissionService.findPermissionsByRole(role)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping("/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPermission(@PathVariable Role role, @RequestParam String permissionCode) {
        permissionService.assignPermission(role, permissionCode);
    }

    @DeleteMapping("/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable Role role, @RequestParam String permissionCode) {
        permissionService.revokePermission(role, permissionCode);
    }

    private PermissionDto toDto(Permission permission) {
        return PermissionDto.builder()
                .code(permission.getCode())
                .description(permission.getDescription())
                .build();
    }
}
