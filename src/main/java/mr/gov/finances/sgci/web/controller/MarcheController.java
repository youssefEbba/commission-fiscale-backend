package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.AssignMarcheDelegueRequest;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.DocumentMarcheDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import mr.gov.finances.sgci.domain.enums.TypeDocumentMarche;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/marches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarcheController {

    private final MarcheService service;

    @GetMapping
    @PreAuthorize("hasAuthority('marche.manage')")
    public List<MarcheDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-correction/{demandeCorrectionId}")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto getByCorrection(
            @PathVariable Long demandeCorrectionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findByDemandeCorrection(demandeCorrectionId, user);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto assignDelegue(
            @PathVariable Long id,
            @RequestBody(required = false) AssignMarcheDelegueRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long delegueId = request != null ? request.getDelegueId() : null;
        return service.assignDelegue(id, delegueId, user);
    }

    @PostMapping("/{id}/delegues")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto addDelegue(
            @PathVariable Long id,
            @RequestBody AssignMarcheDelegueRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (request == null || request.getDelegueId() == null) {
            throw new RuntimeException("delegueId est obligatoire");
        }
        return service.addDelegue(id, request.getDelegueId(), user);
    }

    @DeleteMapping("/{id}/delegues/{delegueId}")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto removeDelegue(
            @PathVariable Long id,
            @PathVariable Long delegueId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.removeDelegue(id, delegueId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto create(
            @Valid @RequestBody CreateMarcheRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(request, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('marche.manage')")
    public MarcheDto update(@PathVariable Long id, @Valid @RequestBody UpdateMarcheRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('marche.manage')")
    public List<DocumentMarcheDto> getDocuments(@PathVariable Long id) {
        return service.findDocuments(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('marche.manage')")
    public DocumentMarcheDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocumentMarche type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.uploadDocument(id, type, file);
    }
}
