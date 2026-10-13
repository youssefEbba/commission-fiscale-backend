package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionTransfertCreditService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.service.TransfertCreditService;
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
@RequestMapping("/api/transferts-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DecisionTransfertCreditController {

    private final DecisionTransfertCreditService decisionService;
    private final RejetTempResponseService rejetTempResponseService;
    private final TransfertCreditService transfertCreditService;

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasAnyAuthority('transfert.solde.view', 'transfert.dgtcp.queue.view', "
            + "'transfert.dgd.visa', 'transfert.dgi.visa', "
            + "'transfert.president.validate', 'transfert.president.reject', 'archivage.view')")
    public List<DecisionCreditDto> getDecisions(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        transfertCreditService.findById(id, user);
        return decisionService.findByTransfert(id);
    }

    @PostMapping("/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    // Les quatre rôles du circuit réclament des compléments, pas seulement le Trésor : c'est ce que
    // DecisionTransfertCreditService impose déjà par ORDRE_VISAS. Cette liste ne l'avait jamais suivi.
    @PreAuthorize("hasAnyAuthority('transfert.dgd.visa', 'transfert.dgi.visa', "
            + "'transfert.dgtcp.update', 'transfert.president.validate', 'transfert.president.reject')")
    public DecisionCreditDto saveDecision(
            @PathVariable Long id,
            @Valid @RequestBody DecisionCreditRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return decisionService.saveDecision(id,
                request.getDecision(),
                request.getMotifRejet(),
                request.getDocumentsDemandes() != null ? new HashSet<>(request.getDocumentsDemandes()) : null,
                user);
    }

    @PostMapping(value = "/decisions/{decisionId}/rejet-temp/reponses", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('transfert.entreprise.rejet.repondre')")
    public List<RejetTempResponseDto> addRejetTempResponseJson(
            @PathVariable Long decisionId,
            @Valid @RequestBody RejetTempResponseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return rejetTempResponseService.addResponseToTransfertDecision(decisionId, request.getMessage(), user);
    }

    @PostMapping(value = "/decisions/{decisionId}/rejet-temp/reponses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('transfert.entreprise.rejet.repondre')")
    public List<RejetTempResponseDto> addRejetTempResponseMultipart(
            @PathVariable Long decisionId,
            @RequestParam("message") String message,
            @RequestParam(value = "type", required = false) String typeParam,
            @RequestParam(value = "typeDocument", required = false) String typeDocumentParam,
            @RequestParam(value = "codeDocument", required = false) String codeDocumentParam,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        String resolvedCode = codeDocumentParam != null && !codeDocumentParam.isBlank() ? codeDocumentParam
                : (typeDocumentParam != null && !typeDocumentParam.isBlank() ? typeDocumentParam : typeParam);
        return decisionService.respondRejetTemp(decisionId, message, file, resolvedCode, user);
    }

    @PutMapping("/decisions/{decisionId}/resolve")
    // Le service exige que le rôle qui résout soit celui qui a posé le rejet : une DGD qui ne
    // pourrait pas résoudre le sien laisserait le circuit bloqué pour toujours.
    @PreAuthorize("hasAnyAuthority('transfert.dgd.visa', 'transfert.dgi.visa', "
            + "'transfert.dgtcp.update', 'transfert.president.validate')")
    public DecisionCreditDto resolveRejetTemp(
            @PathVariable Long decisionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return decisionService.resolveRejetTemp(decisionId, user);
    }
}
