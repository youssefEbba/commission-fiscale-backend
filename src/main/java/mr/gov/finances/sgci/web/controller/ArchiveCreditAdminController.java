package mr.gov.finances.sgci.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.archive.ArchiveCreditAdminService;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.ArchiveCreditDto;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.RattachementRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Crédits repris de l'archive : corriger leur rattachement (entreprise, autorité contractante,
 * convention, marché) ou les supprimer définitivement pour les réimporter. Administrateur seulement.
 */
@RestController
@RequestMapping("/api/archive/credits/{certificatId}")
@RequiredArgsConstructor
@Tag(name = "Archive", description = "Reprise des crédits d'impôt de l'ancienne application")
public class ArchiveCreditAdminController {

    private final ArchiveCreditAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Rattachement actuel d'un crédit d'archive et ce que sa suppression effacerait")
    public ArchiveCreditDto detail(@PathVariable Long certificatId) {
        return service.detail(certificatId);
    }

    @PatchMapping("/rattachement")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Corriger l'entreprise, l'autorité contractante, la convention ou le marché d'un crédit d'archive")
    public ArchiveCreditDto corrigerRattachement(@PathVariable Long certificatId,
                                                 @Valid @RequestBody RattachementRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return service.corrigerRattachement(certificatId, request, user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Supprimer définitivement un crédit d'archive et tout ce qui s'y rattache, pour le réimporter")
    public void supprimer(@PathVariable Long certificatId, @RequestParam String motif,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        service.supprimer(certificatId, motif, user);
    }
}
