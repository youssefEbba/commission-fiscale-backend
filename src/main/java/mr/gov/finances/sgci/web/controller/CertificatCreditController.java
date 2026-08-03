package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.service.CertificatVerificationService;
import mr.gov.finances.sgci.service.DocumentCertificatCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.AdminCorrectionCertificatRequest;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CertificatCreditFicheDto;
import mr.gov.finances.sgci.web.dto.CertificatCreditJournalDto;
import mr.gov.finances.sgci.web.dto.CertificatVerificationDto;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEligibilityDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.web.dto.PageResponse;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.web.dto.DocumentCertificatCreditDto;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.web.dto.TvaDeductibleStockDto;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.io.IOException;

@RestController
@RequestMapping("/api/certificats-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CertificatCreditController {

    private final CertificatCreditService service;
    private final CertificatVerificationService verificationService;
    private final DocumentCertificatCreditService documentService;
    private final UtilisationCreditService utilisationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public List<CertificatCreditDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    /**
     * Vérification par scan du code-barres (valeur = {@code certificat.numero}).
     * Réponse toujours 200 avec {@code trouve=false} si inconnu (UX scanner).
     */
    @GetMapping("/verification")
    @PreAuthorize("hasAuthority('certificat.verification.scan')")
    public CertificatVerificationDto verifyByNumero(@RequestParam String numero) {
        return verificationService.verifyByNumero(numero);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public CertificatCreditDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/{id}/eligibilite-utilisation")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit', 'mise_en_place.entreprise.queue.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view')")
    public CertificatUtilisationEligibilityDto getEligibiliteUtilisation(
            @PathVariable Long id,
            @RequestParam TypeUtilisation type,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.evaluateUtilisationEligibility(id, type, user);
    }

    /**
     * Recherche multi-critères des crédits d'impôt (périmètre du rôle), paginée.
     * Tous les paramètres sont optionnels ; les critères texte sont insensibles à la casse (contient).
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public PageResponse<CertificatCreditDto> search(
            @RequestParam(required = false) String nif,
            @RequestParam(required = false) String numeroMarche,
            @RequestParam(required = false) String conventionRef,
            @RequestParam(required = false) String projet,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) StatutCertificat statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.search(nif, numeroMarche, conventionRef, projet, autoriteContractanteId, statut, from, to, page, size, user);
    }

    /** Journal daté des crédits mis en place (OUVERT / MODIFIE / CLOTURE) + agrégats financiers. */
    @GetMapping("/journal")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public CertificatCreditJournalDto journal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.journal(from, to, page, size, user);
    }

    /**
     * Fiche consolidée d'un crédit d'impôt par référence lisible (ex. {@code CR-01/2025}).
     * La référence contenant un « / », elle est passée en paramètre de requête plutôt qu'en segment d'URL.
     */
    @GetMapping("/fiche")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public CertificatCreditFicheDto getFiche(@RequestParam String reference, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getFicheByReference(reference, user);
    }

    @GetMapping("/by-entreprise/{entrepriseId}")
    @PreAuthorize("hasAnyAuthority('mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view')")
    public List<CertificatCreditDto> getByEntreprise(@PathVariable Long entrepriseId) {
        return service.findByEntreprise(entrepriseId);
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'archivage.view')")
    public List<CertificatCreditDto> getByStatut(@RequestParam StatutCertificat statut, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findByStatut(statut, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mise_en_place.submit')")
    public CertificatCreditDto create(@Valid @RequestBody CreateCertificatCreditRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('mise_en_place.submit')")
    public CertificatCreditDto updateBrouillon(
            @PathVariable Long id,
            @Valid @RequestBody CreateCertificatCreditRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateBrouillon(id, request, user);
    }

    @PostMapping("/{id}/soumettre")
    @PreAuthorize("hasAuthority('mise_en_place.submit')")
    public CertificatCreditDto soumettreBrouillon(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.soumettreBrouillon(id, user);
    }

    /**
     * Prise en charge par un acteur DGI / DGD / DGTCP : passage {@code ENVOYEE} → {@code EN_CONTROLE}.
     * Alternative à {@code PATCH /{id}/statut?statut=EN_CONTROLE} pour les profils ayant uniquement les permissions de file.
     */
    @PostMapping("/{id}/prendre-en-charge")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.dgtcp.queue.view')")
    public CertificatCreditDto prendreEnCharge(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.prendreEnCharge(id, user);
    }

    /** Suppression définitive d'un brouillon uniquement. Les certificats déjà en circuit s'annulent via le statut ANNULE. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('mise_en_place.submit')")
    public void deleteBrouillon(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteBrouillon(id, user);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('mise_en_place.submit', 'mise_en_place.dgi.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgtcp.open_credit', 'mise_en_place.dgtcp.allocate', 'mise_en_place.dgtcp.certificate.generate', 'mise_en_place.dgtcp.certificate.send', 'mise_en_place.president.validate', 'mise_en_place.president.document.generate', 'mise_en_place.president.reject', 'mise_en_place.annuler')")
    public CertificatCreditDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutCertificat statut,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user);
    }

    @PatchMapping("/{id}/montants")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgtcp.open_credit', 'mise_en_place.dgtcp.validate')")
    public CertificatCreditDto updateMontants(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCertificatCreditMontantsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateMontants(id, request, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('mise_en_place.dgi.queue.view', 'mise_en_place.dgtcp.queue.view', 'mise_en_place.dgb.queue.view', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.queue.view', 'mise_en_place.view', 'mise_en_place.entreprise.queue.view', 'archivage.view')")
    public List<DocumentCertificatCreditDto> getDocuments(@PathVariable Long id) {
        return documentService.findByCertificatCreditId(id);
    }

    @GetMapping("/{id}/tva-stock")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.interieur.dgtcp.verify', 'archivage.view')")
    public List<TvaDeductibleStockDto> getTvaStock(@PathVariable Long id) {
        return utilisationService.findTvaStockByCertificat(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('mise_en_place.document.upload', 'mise_en_place.dgd.queue.view', 'mise_en_place.president.signature.upload', 'mise_en_place.president.document.generate')")
    public DocumentCertificatCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam(value = "codeDocument", required = false) String codeDocument,
            @RequestParam(value = "typeDocument", required = false) String typeDocument,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        String resolved = mr.gov.finances.sgci.web.support.DocumentUploadParamResolver
                .resolveCodeDocument(codeDocument, typeDocument, type);
        return documentService.upload(id, resolved, message, file, user);
    }

    /** Correction administrateur d'informations, à tout moment y compris après ouverture (ADMIN_SI, motif obligatoire). */
    @PatchMapping("/{id}/admin-correction")
    @PreAuthorize("hasAuthority('certificat.admin_override')")
    public CertificatCreditDto adminCorrectInfo(
            @PathVariable Long id,
            @RequestParam String motif,
            @RequestBody AdminCorrectionCertificatRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.adminCorrectInfo(id, request, motif, user);
    }

    /** Remplacement administrateur d'un document, à tout moment (ADMIN_SI, motif obligatoire). */
    @PostMapping(value = "/{id}/documents/admin-correction", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('certificat.admin_override')")
    public DocumentCertificatCreditDto adminReplaceDocument(
            @PathVariable Long id,
            @RequestParam String codeDocument,
            @RequestParam String motif,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.adminReplace(id, codeDocument, motif, file, user);
    }
}
