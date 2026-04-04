package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutReferentielProjet;
import mr.gov.finances.sgci.domain.enums.TypeDocumentProjet;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReferentielProjetService;
import mr.gov.finances.sgci.web.dto.CreateReferentielProjetRequest;
import mr.gov.finances.sgci.web.dto.DocumentProjetDto;
import mr.gov.finances.sgci.web.dto.ReferentielProjetDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/referentiels-projet")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReferentielProjetController {

    private final ReferentielProjetService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('projet.view', 'projet.view.all')")
    public List<ReferentielProjetDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('projet.view', 'projet.view.all')")
    public ReferentielProjetDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-autorite/{autoriteId}")
    @PreAuthorize("hasAnyAuthority('projet.view', 'projet.view.all')")
    public List<ReferentielProjetDto> getByAutorite(@PathVariable Long autoriteId) {
        return service.findByAutoriteContractante(autoriteId);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('projet.view', 'projet.view.all')")
    public List<ReferentielProjetDto> getByStatut(@RequestParam StatutReferentielProjet statut) {
        return service.findByStatut(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('projet.create')")
    public ReferentielProjetDto create(
            @Valid @RequestBody CreateReferentielProjetRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.getUserId() : null;
        return service.create(request, userId);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('projet.validate', 'projet.reject')")
    public ReferentielProjetDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutReferentielProjet statut,
            @RequestParam(required = false) String motifRejet,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.getUserId() : null;
        return service.updateStatut(id, statut, userId, motifRejet);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('projet.view', 'projet.view.all')")
    public List<DocumentProjetDto> getDocuments(@PathVariable Long id) {
        return service.findDocuments(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('projet.document.upload')")
    public DocumentProjetDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocumentProjet type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.uploadDocument(id, type, file);
    }

    @PutMapping(value = "/{id}/documents/{documentId}", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('projet.document.upload')")
    public DocumentProjetDto replaceDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.replaceDocument(id, documentId, file);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('projet.document.upload')")
    public void deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        service.deleteDocument(id, documentId);
    }
}
