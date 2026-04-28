package mr.gov.finances.sgci.web.controller;

import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CommissionRelaisService;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.ImpersonateAutoriteRequest;
import mr.gov.finances.sgci.web.dto.ImpersonateEntrepriseRequest;
import mr.gov.finances.sgci.web.dto.LoginResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôle d’accès : {@link mr.gov.finances.sgci.service.CommissionRelaisService} vérifie que le compte en base
 * ({@code userId} du JWT) a le rôle {@link mr.gov.finances.sgci.domain.enums.Role#COMMISSION_RELAIS}.
 * Aucun {@code @PreAuthorize} sur les permissions du JWT : en impersonation le jeton ne porte que les droits
 * ENTREPRISE / AUTORITE_CONTRACTANTE.
 */
@RestController
@RequestMapping("/api/commission-relais")
@RequiredArgsConstructor
public class CommissionRelaisController {

    private final CommissionRelaisService commissionRelaisService;

    @GetMapping("/entreprises")
    public ResponseEntity<Page<EntrepriseDto>> listEntreprises(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(commissionRelaisService.listEntreprises(user, pageable, q));
    }

    @GetMapping("/autorites-contractantes")
    public ResponseEntity<Page<AutoriteContractanteDto>> listAutorites(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(commissionRelaisService.listAutoritesContractantes(user, pageable, q));
    }

    @PostMapping("/impersonate/entreprise")
    public ResponseEntity<LoginResponse> impersonateEntreprise(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImpersonateEntrepriseRequest request
    ) {
        return ResponseEntity.ok(commissionRelaisService.impersonateEntreprise(user, request));
    }

    @PostMapping("/impersonate/autorite-contractante")
    public ResponseEntity<LoginResponse> impersonateAutorite(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImpersonateAutoriteRequest request
    ) {
        return ResponseEntity.ok(commissionRelaisService.impersonateAutorite(user, request));
    }

    @PostMapping("/release")
    public ResponseEntity<LoginResponse> release(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(commissionRelaisService.release(user));
    }
}
