package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.ReferentielTypeDocumentService;
import mr.gov.finances.sgci.web.dto.ReferentielTypeDocumentDto;
import mr.gov.finances.sgci.web.dto.UpsertReferentielTypeDocumentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/referentiel/types-document")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReferentielTypeDocumentController {

    private final ReferentielTypeDocumentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('document.types.view') or hasAuthority('document.types.manage') or hasAuthority('permissions.manage')")
    public List<ReferentielTypeDocumentDto> list(@RequestParam(required = false) Boolean actif) {
        return service.findAll(actif);
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('document.types.view') or hasAuthority('document.types.manage') or hasAuthority('permissions.manage')")
    public ReferentielTypeDocumentDto get(@PathVariable String code) {
        return service.findByCode(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('document.types.manage') or hasAuthority('permissions.manage')")
    public ReferentielTypeDocumentDto create(@Valid @RequestBody UpsertReferentielTypeDocumentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('document.types.manage') or hasAuthority('permissions.manage')")
    public ReferentielTypeDocumentDto update(@PathVariable String code,
                                             @Valid @RequestBody UpsertReferentielTypeDocumentRequest request) {
        return service.update(code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('document.types.manage') or hasAuthority('permissions.manage')")
    public void delete(@PathVariable String code) {
        service.delete(code);
    }
}
