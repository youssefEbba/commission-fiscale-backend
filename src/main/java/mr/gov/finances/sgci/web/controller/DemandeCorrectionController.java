package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/demandes-correction")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandeCorrectionController {

    private final DemandeCorrectionService service;
    private final DocumentService documentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.visa.history.view')")
    public List<DemandeCorrectionDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.visa.history.view')")
    public DemandeCorrectionDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-autorite/{autoriteId}")
    @PreAuthorize("hasAnyAuthority('correction.visa.history.view', 'correction.view.audit')")
    public List<DemandeCorrectionDto> getByAutorite(@PathVariable Long autoriteId) {
        return service.findByAutoriteContractante(autoriteId);
    }

    @GetMapping("/by-entreprise/{entrepriseId}")
    @PreAuthorize("hasAnyAuthority('correction.visa.history.view', 'correction.view.audit', 'correction.entreprise.queue.view')")
    public List<DemandeCorrectionDto> getByEntreprise(@PathVariable Long entrepriseId) {
        return service.findByEntreprise(entrepriseId);
    }

    @GetMapping("/by-delegue/{userId}")
    @PreAuthorize("hasAnyAuthority('correction.offer.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DemandeCorrectionDto> getByDelegue(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findByDelegue(userId, user);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit')")
    public List<DemandeCorrectionDto> getByStatut(@RequestParam StatutDemande statut) {
        return service.findByStatut(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('correction.submit')")
    public DemandeCorrectionDto create(@Valid @RequestBody CreateDemandeCorrectionRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('correction.dgd.save', 'correction.dgd.transmit', 'correction.dgtcp.visa', 'correction.dgtcp.reject', 'correction.dgtcp.request_complements', 'correction.dgi.visa', 'correction.dgi.reject', 'correction.dgb.visa', 'correction.dgb.reject', 'correction.president.validate', 'correction.president.reject', 'correction.president.letter.generate', 'correction.president.signature.upload')")
    public DemandeCorrectionDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutDemande statut,
            @RequestParam(required = false) String motifRejet,
            @RequestParam(required = false) Boolean decisionFinale,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user, motifRejet, decisionFinale);
    }

    /** Liste des documents attachés à une demande de correction (7 pièces P1, etc.) */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('correction.offer.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DocumentDto> getDocuments(@PathVariable Long id) {
        return documentService.findByDemandeCorrectionId(id);
    }

    /** Upload d'un document pour une demande (type = LETTRE_SAISINE, PV_OUVERTURE, OFFRE_FINANCIERE, etc.) */
    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('correction.offer.upload', 'correction.complement.add')")
    public DocumentDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.upload(id, type, file);
    }
}
