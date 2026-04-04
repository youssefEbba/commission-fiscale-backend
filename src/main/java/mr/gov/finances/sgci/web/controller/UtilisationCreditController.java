package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.ApurerTVAInterieureRequest;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/utilisations-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilisationCreditController {

    private final UtilisationCreditService service;
    private final DocumentUtilisationCreditService documentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'archivage.view')")
    public List<UtilisationCreditDto> getAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Boolean demandeurSousTraitantOnly
    ) {
        return service.findAllVisible(user, demandeurSousTraitantOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'archivage.view')")
    public UtilisationCreditDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-certificat/{certificatCreditId}")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'archivage.view')")
    public List<UtilisationCreditDto> getByCertificat(
            @PathVariable Long certificatCreditId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findByCertificatCreditId(certificatCreditId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit')")
    public UtilisationCreditDto create(@Valid @RequestBody CreateUtilisationCreditRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.verify', 'utilisation.douane.dgd.quittance.visa', 'utilisation.douane.dgd.reject', 'utilisation.douane.dgtcp.impute', 'utilisation.douane.dgtcp.solde.update', 'utilisation.interieur.dgtcp.verify', 'utilisation.interieur.dgtcp.validate', 'utilisation.interieur.dgtcp.solde.update', 'utilisation.interieur.dgtcp.reject')")
    public UtilisationCreditDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutUtilisation statut,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user);
    }

    @PostMapping("/{id}/apurement-tva")
    @PreAuthorize("hasAnyAuthority('utilisation.interieur.dgtcp.solde.update')")
    public UtilisationCreditDto apurerTVAInterieure(
            @PathVariable Long id,
            @Valid @RequestBody ApurerTVAInterieureRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.apurerTVAInterieure(id, request, user);
    }

    @PostMapping("/{id}/liquidation-douane")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.impute', 'utilisation.douane.dgtcp.solde.update')")
    public UtilisationCreditDto liquiderDouane(
            @PathVariable Long id,
            @Valid @RequestBody LiquiderUtilisationDouaneRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.liquiderDouane(id, request, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'archivage.view')")
    public List<DocumentUtilisationCreditDto> getDocuments(@PathVariable Long id) {
        return documentService.findByUtilisationCreditId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.document.upload', 'utilisation.interieur.document.upload')")
    public DocumentUtilisationCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.upload(id, type, message, file, user);
    }
}
