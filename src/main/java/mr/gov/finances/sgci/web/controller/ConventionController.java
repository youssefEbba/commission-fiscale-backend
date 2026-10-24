package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.TypeDocumentConvention;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateConventionRequest;
import mr.gov.finances.sgci.web.dto.DocumentConventionDto;
import mr.gov.finances.sgci.web.dto.UpdateConventionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/conventions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConventionController {

    private final ConventionService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('convention.view', 'convention.view.all')")
    public List<ConventionDto> getAll(
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findAll(user, q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('convention.view', 'convention.view.all')")
    public ConventionDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('convention.view', 'convention.view.all')")
    public List<ConventionDto> getByStatut(@RequestParam StatutConvention statut, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findByStatut(statut, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('convention.create')")
    public ConventionDto create(
            @Valid @RequestBody CreateConventionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(request, user);
    }

    /**
     * Modification par l'autorité titulaire ou créatrice, tant que la DGB n'a pas validé.
     * Le service vérifie l'autorité et le statut : la permission seule ne suffit pas.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('convention.update')")
    public ConventionDto update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateConventionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.update(id, request, user);
    }

    /**
     * Annulation par l'autorité titulaire ou créatrice, tant que la DGB n'a pas validé.
     * Les directions continuent d'annuler par {@code PATCH /{id}/statut}.
     */
    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('convention.annuler')")
    public ConventionDto annuler(
            @PathVariable Long id,
            @RequestParam(required = false) String motif,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.annulerParAutorite(id, motif, user);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('convention.validate', 'convention.reject')")
    public ConventionDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutConvention statut,
            @RequestParam(required = false) String motifRejet,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.getUserId() : null;
        return service.updateStatut(id, statut, userId, motifRejet);
    }

    /**
     * Ouvre ou ferme une convention aux nouveaux rattachements.
     *
     * <p>Permission dédiée plutôt que {@code convention.validate} : fermer un financement n'est pas
     * valider une convention, et les deux portes doivent pouvoir être confiées à des rôles
     * différents depuis l'écran des rôles — c'est le « paramétrable » demandé en recette.
     */
    @PatchMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('convention.activate')")
    public ConventionDto changerActivation(
            @PathVariable Long id,
            @RequestParam boolean actif,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.changerActivation(id, actif, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('convention.view', 'convention.view.all')")
    public List<DocumentConventionDto> getDocuments(@PathVariable Long id) {
        return service.findDocuments(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('convention.document.upload')")
    public DocumentConventionDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocumentConvention type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.uploadDocument(id, type, file);
    }

    @PutMapping(value = "/{id}/documents/{documentId}", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('convention.document.upload')")
    public DocumentConventionDto replaceDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.replaceDocument(id, documentId, file);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('convention.document.upload')")
    public void deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        service.deleteDocument(id, documentId);
    }
}
