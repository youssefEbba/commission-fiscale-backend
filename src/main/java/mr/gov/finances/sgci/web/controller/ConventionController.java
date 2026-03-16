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
    public List<ConventionDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
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
        Long userId = user != null ? user.getUserId() : null;
        return service.create(request, userId);
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
}
