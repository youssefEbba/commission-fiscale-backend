package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AvenantService;
import mr.gov.finances.sgci.service.DocumentAvenantService;
import mr.gov.finances.sgci.web.dto.ApplicationAvenantRequest;
import mr.gov.finances.sgci.web.dto.AvenantDto;
import mr.gov.finances.sgci.web.dto.CreateAvenantRequest;
import mr.gov.finances.sgci.web.dto.DocumentAvenantDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final AvenantService service;

    private static final String LECTURE = "hasAnyAuthority('modification.view', 'modification.dgtcp.queue.view', "
            + "'modification.president.queue.view', 'archivage.view')";

    @GetMapping
    @PreAuthorize(LECTURE)
    public List<AvenantDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTURE)
    public AvenantDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-certificat/{certificatId}")
    @PreAuthorize(LECTURE)
    public List<AvenantDto> getByCertificat(@PathVariable Long certificatId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findByCertificat(certificatId, user);
    }

    /** Dépôt par l'entreprise ou l'autorité contractante, sur son crédit ouvert. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('modification.submit')")
    public AvenantDto deposer(@Valid @RequestBody CreateAvenantRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return service.deposer(request, user);
    }

    /** Examen par la DGTCP (l'administrateur s'y substitue avec un motif). */
    @PostMapping("/{id}/prise-en-charge")
    @PreAuthorize("hasAnyAuthority('modification.dgtcp.analyze', 'modification.admin_override')")
    public AvenantDto prendreEnCharge(@PathVariable Long id, @RequestParam(required = false) String motif,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return service.prendreEnCharge(id, motif, user);
    }

    /** Validation par le Président ; pour un avenant financier, crée la demande de correction. */
    @PostMapping("/{id}/validation")
    @PreAuthorize("hasAnyAuthority('modification.president.validate', 'modification.admin_override')")
    public AvenantDto valider(@PathVariable Long id, @RequestParam(required = false) String motif,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return service.valider(id, motif, user);
    }

    /** Rejet par la DGTCP à l'examen, ou par le Président à la validation. Motif obligatoire. */
    @PostMapping("/{id}/rejet")
    @PreAuthorize("hasAnyAuthority('modification.dgtcp.analyze', 'modification.president.reject', 'modification.admin_override')")
    public AvenantDto rejeter(@PathVariable Long id, @RequestParam String motif,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return service.rejeter(id, motif, user);
    }

    /** Application par la DGTCP ; le nouveau récapitulatif est exigé pour un avenant financier. */
    @PostMapping("/{id}/application")
    @PreAuthorize("hasAuthority('modification.dgtcp.propose')")
    public AvenantDto appliquer(@PathVariable Long id,
                                @RequestBody(required = false) ApplicationAvenantRequest recap,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return service.appliquer(id, recap, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize(LECTURE)
    public List<DocumentAvenantDto> getDocuments(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.charger(id, user);
        return documentService.findByAvenantId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('modification.document.upload')")
    public DocumentAvenantDto uploadDocument(
            @PathVariable Long id,
            @RequestParam(value = "codeDocument", required = false) String codeDocument,
            @RequestParam(value = "typeDocument", required = false) String typeDocument,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        String resolved = mr.gov.finances.sgci.web.support.DocumentUploadParamResolver
                .resolveCodeDocument(codeDocument, typeDocument, type);
        // Le périmètre du crédit, et tant que l'avenant est déposé : ses pièces accompagnent le dépôt.
        var avenant = service.charger(id, user);
        if (avenant.getStatut() != mr.gov.finances.sgci.domain.enums.StatutAvenant.DEPOSE) {
            throw mr.gov.finances.sgci.web.exception.ApiException.conflict(
                    mr.gov.finances.sgci.web.exception.ApiErrorCode.CONFLICT,
                    "Les pièces se déposent tant que l'avenant n'est pas pris en charge");
        }
        return documentService.upload(id, resolved, file);
    }
}
