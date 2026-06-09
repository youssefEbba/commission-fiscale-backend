package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.PasswordResetService;
import mr.gov.finances.sgci.web.dto.DemandeResetPasswordDto;
import mr.gov.finances.sgci.web.dto.RejectPasswordResetRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs/password-reset-requests")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PasswordResetAdminController {

    private final PasswordResetService passwordResetService;

    @GetMapping
    @PreAuthorize("hasAuthority('user.reset')")
    public List<DemandeResetPasswordDto> list(
            @RequestParam(required = false) StatutDemandeResetPassword statut) {
        return passwordResetService.listRequests(statut);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.reset')")
    public DemandeResetPasswordDto get(@PathVariable Long id) {
        return passwordResetService.getRequest(id);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('user.reset')")
    public DemandeResetPasswordDto approve(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return passwordResetService.approve(id, admin.getUserId());
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('user.reset')")
    public DemandeResetPasswordDto reject(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser admin,
            @RequestBody(required = false) RejectPasswordResetRequest body) {
        String motif = body != null ? body.getMotif() : null;
        return passwordResetService.reject(id, admin.getUserId(), motif);
    }
}
