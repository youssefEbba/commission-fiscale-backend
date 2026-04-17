package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.service.DocumentService;
import mr.gov.finances.sgci.service.ReclamationDemandeCorrectionService;
import mr.gov.finances.sgci.web.dto.ReclamationDemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;

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
public class DemandeCorrectionController {

    private final DemandeCorrectionService service;
    private final DocumentService documentService;
    private final ReclamationDemandeCorrectionService reclamationDemandeCorrectionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.visa.history.view', 'correction.entreprise.queue.view')")
    public List<DemandeCorrectionDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.visa.history.view', 'correction.entreprise.queue.view')")
    public DemandeCorrectionDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-autorite/{autoriteId}")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgb.queue.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DemandeCorrectionDto> getByAutorite(@PathVariable Long autoriteId) {
        return service.findByAutoriteContractante(autoriteId);
    }

    @GetMapping("/by-entreprise/{entrepriseId}")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgb.queue.view', 'correction.visa.history.view', 'correction.view.audit', 'correction.entreprise.queue.view')")
    public List<DemandeCorrectionDto> getByEntreprise(@PathVariable Long entrepriseId) {
        return service.findByEntreprise(entrepriseId);
    }

    @GetMapping("/by-delegue/{userId}")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgb.queue.view', 'correction.offer.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DemandeCorrectionDto> getByDelegue(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findByDelegue(userId, user);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.entreprise.queue.view')")
    public List<DemandeCorrectionDto> getByStatut(@RequestParam StatutDemande statut) {
        return service.findByStatut(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('correction.submit')")
    public DemandeCorrectionDto create(
            @Valid @RequestBody CreateDemandeCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(request, user);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('correction.submit', 'correction.demande.reactivate', 'correction.dgd.save', 'correction.dgd.transmit', 'correction.dgtcp.visa', 'correction.dgtcp.reject', 'correction.dgtcp.request_complements', 'correction.dgi.visa', 'correction.dgi.reject', 'correction.dgb.visa', 'correction.dgb.reject', 'correction.president.validate', 'correction.president.reject', 'correction.president.letter.generate', 'correction.president.signature.upload')")
    public DemandeCorrectionDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutDemande statut,
            @RequestParam(required = false) String motifRejet,
            @RequestParam(required = false) Boolean decisionFinale,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user, motifRejet, decisionFinale);
    }

    /** Liste des documents attachés à une demande de correction (7 pièces P1, etc.) */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('correction.dgd.queue.view', 'correction.offer.view', 'correction.visa.history.view', 'correction.view.audit')")
    public List<DocumentDto> getDocuments(@PathVariable Long id) {
        return documentService.findByDemandeCorrectionId(id);
    }

    /** Upload d'un document pour une demande (type = LETTRE_SAISINE, PV_OUVERTURE, OFFRE_FINANCIERE, etc.) */
    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('correction.offer.upload', 'correction.complement.add')")
    public DocumentDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.upload(id, type, message, file, user);
    }

    /** Réclamations sur une demande adoptée ou notifiée (dépôt AC / délégués / entreprise). */
    @GetMapping("/{id}/reclamations")
    @PreAuthorize("hasAnyAuthority('demande_correction.list', 'correction.dgd.queue.view', 'correction.dgtcp.queue.view', 'correction.dgi.queue.view', 'correction.dgb.queue.view', 'correction.president.queue.view', 'correction.view.audit', 'correction.visa.history.view', 'correction.entreprise.queue.view', 'correction.reclamation.submit', 'correction.reclamation.annuler', 'correction.reclamation.traiter')")
    public List<ReclamationDemandeCorrectionDto> listReclamations(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return reclamationDemandeCorrectionService.listByDemande(id, user);
    }

    @PostMapping(value = "/{id}/reclamations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('correction.reclamation.submit')")
    public ReclamationDemandeCorrectionDto createReclamation(
            @PathVariable Long id,
            @RequestParam("texte") String texte,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return reclamationDemandeCorrectionService.create(id, texte, file, user);
    }

    /**
     * Acceptation (DGTCP) ou rejet (DGTCP / Président) : {@code multipart/form-data}.
     * Rejet : {@code motifReponse} + {@code file} obligatoires. Acceptation : ne pas envoyer de {@code file}.
     */
    @PatchMapping(value = "/{demandeId}/reclamations/{reclamationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('correction.reclamation.traiter')")
    public ReclamationDemandeCorrectionDto traiterReclamation(
            @PathVariable Long demandeId,
            @PathVariable Long reclamationId,
            @RequestParam("acceptee") String acceptee,
            @RequestParam(value = "motifReponse", required = false) String motifReponse,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        boolean accept = acceptee != null && ("true".equalsIgnoreCase(acceptee.trim()) || "1".equals(acceptee.trim()));
        return reclamationDemandeCorrectionService.traiter(demandeId, reclamationId, accept, motifReponse, file, user);
    }

    /** Annule une réclamation SOUMISE avant DGTCP : statut de la demande et visas inchangés (auteur ou AC du dossier). */
    @PostMapping("/{demandeId}/reclamations/{reclamationId}/annuler")
    @PreAuthorize("hasAuthority('correction.reclamation.annuler')")
    public ReclamationDemandeCorrectionDto annulerReclamation(
            @PathVariable Long demandeId,
            @PathVariable Long reclamationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return reclamationDemandeCorrectionService.annuler(demandeId, reclamationId, user);
    }
}
