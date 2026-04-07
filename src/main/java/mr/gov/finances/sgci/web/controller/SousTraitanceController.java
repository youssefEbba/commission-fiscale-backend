package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentSousTraitanceService;
import mr.gov.finances.sgci.service.SousTraitanceService;
import mr.gov.finances.sgci.web.dto.CreateSousTraitanceOnboardingRequest;
import mr.gov.finances.sgci.web.dto.CreateSousTraitanceRequest;
import mr.gov.finances.sgci.web.dto.DocumentSousTraitanceDto;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.SousTraitanceDto;
import mr.gov.finances.sgci.web.dto.SousTraitanceOnboardingResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/sous-traitances")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SousTraitanceController {

    private final SousTraitanceService service;
    private final DocumentSousTraitanceService documentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('sous_traitance.solde.view', 'sous_traitance.dgtcp.queue.view', 'archivage.view')")
    public List<SousTraitanceDto> getAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long sousTraitantEntrepriseId
    ) {
        return service.findAll(user, sousTraitantEntrepriseId);
    }

    @GetMapping("/entreprises-sous-traitantes")
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public List<EntrepriseDto> listEntreprisesSousTraitantes(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findSousTraitantEntreprisesForTitulaire(user);
    }

    @GetMapping("/by-certificat/{certificatCreditId}")
    @PreAuthorize("hasAnyAuthority('sous_traitance.solde.view', 'sous_traitance.dgtcp.queue.view', 'archivage.view')")
    public SousTraitanceDto getByCertificat(
            @PathVariable Long certificatCreditId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        SousTraitanceDto dto = service.findByCertificatCreditId(certificatCreditId, user);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune sous-traitance pour ce certificat");
        }
        return dto;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('sous_traitance.solde.view', 'sous_traitance.dgtcp.queue.view', 'archivage.view')")
    public SousTraitanceDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public SousTraitanceDto create(@Valid @RequestBody CreateSousTraitanceRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public SousTraitanceOnboardingResultDto onboard(@Valid @RequestBody CreateSousTraitanceOnboardingRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return service.onboard(request, user);
    }

    @PostMapping("/{id}/autoriser")
    @PreAuthorize("hasAuthority('sous_traitance.dgtcp.update')")
    public SousTraitanceDto autoriser(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.autoriserByDgtcp(id, user);
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('sous_traitance.dgtcp.update')")
    public SousTraitanceDto refuser(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.refuserByDgtcp(id, user);
    }

    @PostMapping("/{id}/suspendre-titulaire")
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public SousTraitanceDto suspendreTitulaire(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.suspendreParTitulaire(id, user);
    }

    @PostMapping("/{id}/reactiver-titulaire")
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public SousTraitanceDto reactiverTitulaire(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.reactiverParTitulaire(id, user);
    }

    @PostMapping("/{id}/revoquer-titulaire")
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public SousTraitanceDto revoquerTitulaire(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.revoquerParTitulaire(id, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('sous_traitance.solde.view', 'sous_traitance.dgtcp.queue.view', 'archivage.view')")
    public List<DocumentSousTraitanceDto> getDocuments(@PathVariable Long id) {
        return documentService.findBySousTraitanceId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sous_traitance.submit')")
    public DocumentSousTraitanceDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.upload(id, type, file);
    }
}
