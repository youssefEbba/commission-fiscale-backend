package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCorrectionService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.web.dto.AdminVisaCorrectionResultDto;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionRequest;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseRequest;
import mr.gov.finances.sgci.web.dto.VisaCorrectionStatutDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /**
     * Etat des visas de la commission (DGD, DGTCP, DGI, DGB puis Président) : visas déjà posés,
     * document exigé avant chaque visa, et possibilité pour l'administrateur de viser à la place
     * du titulaire. Alimente l'écran administrateur de la demande.
     */
    @GetMapping("/{id}/visas")
    @PreAuthorize("hasAnyAuthority('correction.visa.admin_override', 'correction.dgd.queue.view', 'correction.visa.history.view', 'correction.view.audit', 'correction.president.queue.view')")
    public List<VisaCorrectionStatutDto> getVisaStatuts(@PathVariable Long id) {
        return decisionService.visaStatuts(id);
    }

    /**
     * Visa posé par l'administrateur (ADMIN_SI) à la place d'un membre de la commission, ou
     * adoption prononcée à la place du Président ({@code role=PRESIDENT}).
     * <p>
     * {@code multipart/form-data} : {@code role} et {@code motif} obligatoires, {@code file}
     * obligatoire lorsque le visa exige un document non encore déposé (offre fiscale corrigée pour
     * la DGD, document crédit intérieur pour la DGI, lettre d'adoption pour le Président).
     */
    @PostMapping(value = "/{id}/visas/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('correction.visa.admin_override')")
    public AdminVisaCorrectionResultDto adminVisa(
            @PathVariable Long id,
            @RequestParam("role") Role role,
            @RequestParam("motif") String motif,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return decisionService.adminVisaPourRole(id, role, motif, file, user);
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

    /**
     * Clôturer un REJET_TEMP ouvert : seul le rôle qui a émis la décision peut résoudre (voir {@code DecisionCorrectionService}).
     * Les permissions ici doivent couvrir DGD / DGTCP / DGI / DGB (visa, rejet, save) en plus de l’AC (compléments),
     * sinon un contrôleur émetteur reçoit 403 avant la vérification métier.
     */
    @PutMapping("/decisions/{decisionId}/resolve")
    @PreAuthorize("hasAnyAuthority("
            + "'correction.offer.upload', 'correction.complement.add', "
            + "'correction.dgd.save', "
            + "'correction.dgtcp.visa', 'correction.dgtcp.reject', "
            + "'correction.dgi.visa', 'correction.dgi.reject', "
            + "'correction.dgb.visa', 'correction.dgb.reject'"
            + ")")
    public DecisionCorrectionDto resolveRejetTemp(
            @PathVariable Long decisionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return decisionService.resolveRejetTemp(decisionId, user);
    }
}
