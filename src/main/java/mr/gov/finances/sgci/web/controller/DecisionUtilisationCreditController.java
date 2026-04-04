package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.service.DecisionUtilisationCreditService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.DecisionCreditRequest;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/utilisations-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DecisionUtilisationCreditController {

    private final DecisionUtilisationCreditService service;
    private final RejetTempResponseService rejetTempResponseService;

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'archivage.view')")
    public List<DecisionCreditDto> getDecisions(@PathVariable Long id) {
        return service.findByUtilisation(id);
    }

    @PostMapping("/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.verify', 'utilisation.douane.dgd.quittance.visa', 'utilisation.douane.dgd.reject', 'utilisation.douane.dgtcp.impute', 'utilisation.douane.dgtcp.solde.update', 'utilisation.interieur.dgtcp.verify', 'utilisation.interieur.dgtcp.validate', 'utilisation.interieur.dgtcp.reject', 'utilisation.interieur.dgi.decision')")
    public DecisionCreditDto saveDecision(
            @PathVariable Long id,
            @Valid @RequestBody DecisionCreditRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.saveDecision(id,
                request.getDecision(),
                request.getMotifRejet(),
                request.getDocumentsDemandes() != null ? new HashSet<>(request.getDocumentsDemandes()) : null,
                user);
    }

    @PostMapping(value = "/decisions/{decisionId}/rejet-temp/reponses", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.document.upload', 'utilisation.interieur.document.upload', 'utilisation.entreprise.rejet.repondre')")
    public List<RejetTempResponseDto> addRejetTempResponseJson(
            @PathVariable Long decisionId,
            @Valid @RequestBody RejetTempResponseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return rejetTempResponseService.addResponseToUtilisationDecision(decisionId, request.getMessage(), user);
    }

    @PostMapping(value = "/decisions/{decisionId}/rejet-temp/reponses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.document.upload', 'utilisation.interieur.document.upload', 'utilisation.entreprise.rejet.repondre')")
    public List<RejetTempResponseDto> addRejetTempResponseMultipart(
            @PathVariable Long decisionId,
            @RequestParam("message") String message,
            @RequestParam(value = "type", required = false) TypeDocument type,
            @RequestParam(value = "typeDocument", required = false) TypeDocument typeDocument,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        TypeDocument resolvedType = type != null ? type : typeDocument;
        return service.respondRejetTemp(decisionId, message, file, resolvedType, user);
    }

    @PutMapping("/decisions/{decisionId}/resolve")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.resolve', 'utilisation.douane.dgtcp.resolve', 'utilisation.interieur.dgtcp.resolve', 'utilisation.interieur.dgi.resolve')")
    public DecisionCreditDto resolveRejetTemp(
            @PathVariable Long decisionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.resolveRejetTemp(decisionId, user);
    }
}
