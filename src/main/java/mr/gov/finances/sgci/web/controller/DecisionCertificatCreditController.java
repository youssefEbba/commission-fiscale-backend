package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionCertificatCreditService;
import mr.gov.finances.sgci.service.RejetTempResponseService;
import mr.gov.finances.sgci.web.dto.AdminVisaCertificatResultDto;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.DecisionCreditRequest;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseRequest;
import mr.gov.finances.sgci.web.dto.VisaCertificatStatutDto;
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

    /**
     * État des visas de la commission (DGI, DGD, DGTCP puis Président) : visas déjà posés, document
     * exigé avant chaque visa et possibilité pour l'administrateur de viser à la place du titulaire.
     * Alimente l'écran administrateur du certificat.
     */
    @GetMapping("/{id}/visas")
    @PreAuthorize("hasAnyAuthority('certificat.visa.admin_override', 'mise_en_place.dgi.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'archivage.view')")
    public List<VisaCertificatStatutDto> getVisaStatuts(@PathVariable Long id) {
        return service.visaStatuts(id);
    }

    /**
     * Visa posé par l'administrateur (ADMIN_SI) à la place d'un membre de la commission
     * (DGI / DGD / DGTCP), ou validation prononcée à la place du Président ({@code role=PRESIDENT}).
     * <p>
     * {@code multipart/form-data} : {@code role} et {@code motif} obligatoires, {@code file}
     * obligatoire pour {@code role=PRESIDENT} tant que le certificat signé
     * ({@code CERTIFICAT_CREDIT_IMPOTS}) n'a pas été déposé, et refusé pour les autres rôles.
     * <p>
     * L'ouverture du crédit qui suit la validation présidentielle est une action distincte :
     * {@code POST /api/certificats-credit/{id}/ouverture/admin}.
     */
    @PostMapping(value = "/{id}/visas/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('certificat.visa.admin_override')")
    public AdminVisaCertificatResultDto adminVisa(
            @PathVariable Long id,
            @RequestParam("role") Role role,
            @RequestParam("motif") String motif,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return service.adminVisaPourRole(id, role, motif, file, user);
    }

    /**
     * Résolution d'un rejet temporaire par l'administrateur, quel que soit le rôle qui l'a posé.
     * Un rejet resté ouvert bloque le visa du rôle concerné, y compris le visa administrateur.
     */
    @PutMapping("/decisions/{decisionId}/resolve/admin")
    @PreAuthorize("hasAuthority('certificat.visa.admin_override')")
    public DecisionCreditDto adminResoudreRejetTemp(
            @PathVariable Long decisionId,
            @RequestParam String motif,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.adminResoudreRejetTemp(decisionId, motif, user);
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
