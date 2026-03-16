package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ClotureCreditService;
import mr.gov.finances.sgci.service.DocumentClotureCreditService;
import mr.gov.finances.sgci.web.dto.ClotureCreditDto;
import mr.gov.finances.sgci.web.dto.CreateClotureCreditRequest;
import mr.gov.finances.sgci.web.dto.DocumentClotureCreditDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/clotures-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClotureCreditController {

    private final ClotureCreditService service;
    private final DocumentClotureCreditService documentService;

    @GetMapping("/eligible")
    @PreAuthorize("hasAuthority('cloture.queue.view')")
    public List<Long> listEligibleCertificats(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findEligibleCertificatIds(user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('cloture.queue.view', 'cloture.president.queue.view', 'archivage.view')")
    public List<DocumentClotureCreditDto> getDocuments(@PathVariable Long id) {
        return documentService.findByClotureCreditId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cloture.prepare')")
    public DocumentClotureCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.upload(id, type, file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cloture.prepare')")
    public ClotureCreditDto proposer(@Valid @RequestBody CreateClotureCreditRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return service.proposer(request, user);
    }

    @GetMapping("/propositions")
    @PreAuthorize("hasAuthority('cloture.president.queue.view')")
    public List<ClotureCreditDto> presidentQueue() {
        return service.findPendingPropositions();
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAuthority('cloture.president.validate')")
    public ClotureCreditDto valider(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.validerParPresident(id, user);
    }

    @PostMapping("/{id}/rejeter")
    @PreAuthorize("hasAuthority('cloture.president.reject')")
    public ClotureCreditDto rejeter(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.rejeterParPresident(id, user);
    }

    @PostMapping("/{id}/finaliser")
    @PreAuthorize("hasAuthority('cloture.prepare')")
    public ClotureCreditDto finaliser(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.finaliserParDgtcp(id, user);
    }
}
