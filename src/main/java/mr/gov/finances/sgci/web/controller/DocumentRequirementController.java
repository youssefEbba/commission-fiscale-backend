package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.UpsertDocumentRequirementRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-requirements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentRequirementController {

    private final DocumentRequirementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('document.requirements.view') or hasAuthority('permissions.manage')")
    public List<DocumentRequirementDto> getByProcessus(@RequestParam ProcessusDocument processus) {
        return service.findByProcessus(processus);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('permissions.manage')")
    public DocumentRequirementDto create(@Valid @RequestBody UpsertDocumentRequirementRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('permissions.manage')")
    public DocumentRequirementDto update(@PathVariable Long id, @Valid @RequestBody UpsertDocumentRequirementRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('permissions.manage')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
