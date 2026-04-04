package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCorrectionService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionRequest;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demandes-correction")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DecisionCorrectionController {

    private final DecisionCorrectionService decisionService;
    private final RejetTempResponseService rejetTempResponseService;

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasAnyAuthority('correction.dgd.queue.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DecisionCorrectionDto> getDecisions(@PathVariable Long id) {
        return decisionService.findByDemande(id);
    }

    @PostMapping("/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('correction.dgd.save', 'correction.dgtcp.visa', 'correction.dgi.visa', 'correction.dgb.visa', 'correction.dgtcp.reject', 'correction.dgi.reject', 'correction.dgb.reject')")
    public DecisionCorrectionDto saveDecision(
            @PathVariable Long id,
            @Valid @RequestBody DecisionCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return decisionService.saveDecision(id,
                request.getDecision(),
                request.getMotifRejet(),
                request.getDocumentsDemandes() != null ? new java.util.HashSet<>(request.getDocumentsDemandes()) : null,
                user);
    }

    @PostMapping("/decisions/{decisionId}/rejet-temp/reponses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('correction.offer.upload', 'correction.complement.add')")
    public List<RejetTempResponseDto> addRejetTempResponse(
            @PathVariable Long decisionId,
            @Valid @RequestBody RejetTempResponseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return rejetTempResponseService.addResponseToCorrectionDecision(decisionId, request.getMessage(), user);
    }

    @PutMapping("/decisions/{decisionId}/resolve")
    @PreAuthorize("hasAnyAuthority('correction.offer.upload', 'correction.complement.add')")
    public DecisionCorrectionDto resolveRejetTemp(
            @PathVariable Long decisionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return decisionService.resolveRejetTemp(decisionId, user);
    }
}
