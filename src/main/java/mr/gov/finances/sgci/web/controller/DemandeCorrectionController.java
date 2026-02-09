package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;

import org.springframework.http.HttpStatus;
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
    public List<DemandeCorrectionDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DemandeCorrectionDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-autorite/{autoriteId}")
    public List<DemandeCorrectionDto> getByAutorite(@PathVariable Long autoriteId) {
        return service.findByAutoriteContractante(autoriteId);
    }

    @GetMapping("/by-statut")
    public List<DemandeCorrectionDto> getByStatut(@RequestParam StatutDemande statut) {
        return service.findByStatut(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemandeCorrectionDto create(@Valid @RequestBody CreateDemandeCorrectionRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/statut")
    public DemandeCorrectionDto updateStatut(@PathVariable Long id, @RequestParam StatutDemande statut) {
        return service.updateStatut(id, statut);
    }

    /** Liste des documents attachés à une demande de correction (7 pièces P1, etc.) */
    @GetMapping("/{id}/documents")
    public List<DocumentDto> getDocuments(@PathVariable Long id) {
        return documentService.findByDemandeCorrectionId(id);
    }

    /** Upload d'un document pour une demande (type = LETTRE_SAISINE, PV_OUVERTURE, OFFRE_FINANCIERE, etc.) */
    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.upload(id, type, file);
    }
}
