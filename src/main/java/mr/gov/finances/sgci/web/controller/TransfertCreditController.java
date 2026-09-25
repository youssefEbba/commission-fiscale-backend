package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionTransfertCreditService;
import mr.gov.finances.sgci.service.DocumentTransfertCreditService;
import mr.gov.finances.sgci.service.TransfertCreditService;
import mr.gov.finances.sgci.web.dto.CreateTransfertCreditRequest;
import mr.gov.finances.sgci.web.dto.DocumentTransfertCreditDto;
import mr.gov.finances.sgci.web.dto.VisaTransfertStatutDto;
import mr.gov.finances.sgci.web.dto.TransfertCreditDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/transferts-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransfertCreditController {

    private final TransfertCreditService service;
    private final DocumentTransfertCreditService documentService;
    private final DecisionTransfertCreditService decisionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.president.validate', 'transfert.president.reject', 'archivage.view')")
    public List<TransfertCreditDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.president.validate', 'transfert.president.reject', 'archivage.view')")
    public TransfertCreditDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-certificat/{certificatCreditId}")
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.president.validate', 'transfert.president.reject', 'archivage.view')")
    public TransfertCreditDto getByCertificat(
            @PathVariable Long certificatCreditId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        TransfertCreditDto dto = service.findByCertificatCreditId(certificatCreditId, user);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune demande de transfert pour ce certificat");
        }
        return dto;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('transfert.submit')")
    public TransfertCreditDto create(@Valid @RequestBody CreateTransfertCreditRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    /**
     * État des quatre visas du circuit P7, dans l'ordre DGD → DGI → DGTCP → Président.
     *
     * <p>Alimente la file d'attente de chaque direction et le dossier consolidé soumis au Président.
     * Le blocage est porté par un code stable : le client ne réécrit pas la règle de séquencement.
     */
    @GetMapping("/{id}/visas")
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.dgd.visa', 'transfert.dgi.visa', 'transfert.president.validate', 'archivage.view')")
    public List<VisaTransfertStatutDto> visaStatuts(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return decisionService.visaStatuts(id, user);
    }

    /**
     * Visa d'une direction du circuit (DGD, DGI ou DGTCP), posé à son tour.
     *
     * <p>L'approbation du Président, qui déclenche l'écriture, passe par {@code POST .../valider}.
     */
    @PostMapping("/{id}/visa")
    @PreAuthorize("hasAnyAuthority('transfert.dgd.visa', 'transfert.dgi.visa', 'transfert.dgtcp.update')")
    public TransfertCreditDto viser(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.viserParDirection(id, user);
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyAuthority('transfert.dgtcp.update', 'transfert.president.validate')")
    public TransfertCreditDto validateByDgtcp(@PathVariable Long id,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return service.validateByDgtcp(id, user);
    }

    @PostMapping("/{id}/rejeter")
    @PreAuthorize("hasAnyAuthority('transfert.dgtcp.update', 'transfert.president.reject')")
    public TransfertCreditDto rejectByDgtcp(@PathVariable Long id,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.rejectByDgtcp(id, user);
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('transfert.annuler')")
    public TransfertCreditDto annuler(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.annuler(id, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.president.validate', 'transfert.president.reject', 'archivage.view')")
    public List<DocumentTransfertCreditDto> getDocuments(@PathVariable Long id) {
        return documentService.findByTransfertCreditId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('transfert.submit')")
    public DocumentTransfertCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam(value = "codeDocument", required = false) String codeDocument,
            @RequestParam(value = "typeDocument", required = false) String typeDocument,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        String resolved = mr.gov.finances.sgci.web.support.DocumentUploadParamResolver
                .resolveCodeDocument(codeDocument, typeDocument, type);
        return documentService.upload(id, resolved, message, file, user);
    }
}
