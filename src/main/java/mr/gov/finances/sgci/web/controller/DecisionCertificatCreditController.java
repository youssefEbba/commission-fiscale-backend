package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCertificatCreditService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.DecisionCreditRequest;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/certificats-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DecisionCertificatCreditController {

    private final DecisionCertificatCreditService service;
    private final RejetTempResponseService rejetTempResponseService;

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'archivage.view')")
    public List<DecisionCreditDto> getDecisions(@PathVariable Long id) {
        return service.findByCertificat(id);
    }

    @PostMapping("/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.validate', 'mise_en_place.dgi.reject', 'mise_en_place.dgd.validate', 'mise_en_place.dgd.reject', 'mise_en_place.dgtcp.validate', 'mise_en_place.dgtcp.reject')")
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

    @PostMapping("/decisions/{decisionId}/rejet-temp/reponses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('mise_en_place.document.upload', 'mise_en_place.dgd.queue.view', 'mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.view')")
    public List<RejetTempResponseDto> addRejetTempResponse(
            @PathVariable Long decisionId,
            @Valid @RequestBody RejetTempResponseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return rejetTempResponseService.addResponseToCertificatDecision(decisionId, request.getMessage(), user);
    }

    @PutMapping("/decisions/{decisionId}/resolve")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgd.resolve', 'mise_en_place.dgi.resolve', 'mise_en_place.dgtcp.resolve')")
    public DecisionCreditDto resolveRejetTemp(
            @PathVariable Long decisionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.resolveRejetTemp(decisionId, user);
    }
}
