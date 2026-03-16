package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.service.DocumentAvenantService;
import mr.gov.finances.sgci.web.dto.DocumentAvenantDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/avenants")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AvenantController {

    private final DocumentAvenantService documentService;

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('modification.view', 'archivage.view')")
    public List<DocumentAvenantDto> getDocuments(@PathVariable Long id) {
        return documentService.findByAvenantId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('modification.document.upload')")
    public DocumentAvenantDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.upload(id, type, file);
    }
}
