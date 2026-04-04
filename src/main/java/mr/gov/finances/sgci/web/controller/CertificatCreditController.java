package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.service.DocumentCertificatCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.web.dto.DocumentCertificatCreditDto;
import mr.gov.finances.sgci.web.dto.TvaDeductibleStockDto;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.io.IOException;

@RestController
@RequestMapping("/api/certificats-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CertificatCreditController {

    private final CertificatCreditService service;
    private final DocumentCertificatCreditService documentService;
    private final UtilisationCreditService utilisationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public List<CertificatCreditDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public CertificatCreditDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-entreprise/{entrepriseId}")
    @PreAuthorize("hasAnyAuthority('mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view')")
    public List<CertificatCreditDto> getByEntreprise(@PathVariable Long entrepriseId) {
        return service.findByEntreprise(entrepriseId);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'archivage.view')")
    public List<CertificatCreditDto> getByStatut(@RequestParam StatutCertificat statut, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findByStatut(statut, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mise_en_place.submit')")
    public CertificatCreditDto create(@Valid @RequestBody CreateCertificatCreditRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('mise_en_place.submit', 'mise_en_place.dgtcp.open_credit', 'mise_en_place.dgtcp.allocate', 'mise_en_place.dgtcp.certificate.generate', 'mise_en_place.dgtcp.certificate.send', 'mise_en_place.president.validate', 'mise_en_place.president.document.generate', 'mise_en_place.president.reject', 'mise_en_place.annuler')")
    public CertificatCreditDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutCertificat statut,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user);
    }

    @PatchMapping("/{id}/montants")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgtcp.open_credit', 'mise_en_place.dgtcp.validate')")
    public CertificatCreditDto updateMontants(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCertificatCreditMontantsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateMontants(id, request, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public List<DocumentCertificatCreditDto> getDocuments(@PathVariable Long id) {
        return documentService.findByCertificatCreditId(id);
    }

    @GetMapping("/{id}/tva-stock")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.interieur.dgtcp.verify', 'archivage.view')")
    public List<TvaDeductibleStockDto> getTvaStock(@PathVariable Long id) {
        return utilisationService.findTvaStockByCertificat(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('mise_en_place.document.upload', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.signature.upload', 'mise_en_place.president.document.generate')")
    public DocumentCertificatCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.upload(id, type, message, file, user);
    }
}
