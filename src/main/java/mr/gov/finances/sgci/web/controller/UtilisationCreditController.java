package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.LigneBulletinLiquidationRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.ApurerTVAInterieureRequest;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LigneBulletinDto;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
import mr.gov.finances.sgci.web.dto.QuittanceTresorDto;
import mr.gov.finances.sgci.web.dto.SaisirChequeRequest;
import mr.gov.finances.sgci.web.dto.SaisirQuittancesRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/utilisations-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilisationCreditController {

    private final UtilisationCreditService service;
    private final DocumentUtilisationCreditService documentService;
    private final LigneBulletinLiquidationRepository ligneBulletinRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'utilisation.ac.view', 'archivage.view')")
    public List<UtilisationCreditDto> getAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Boolean demandeurSousTraitantOnly,
            @RequestParam(required = false) Long sousTraitantEntrepriseId
    ) {
        return service.findAllVisible(user, demandeurSousTraitantOnly, sousTraitantEntrepriseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'utilisation.ac.view', 'archivage.view')")
    public UtilisationCreditDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }

    @GetMapping("/by-certificat/{certificatCreditId}")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.douane.history.view', 'utilisation.interieur.history.view', 'archivage.view')")
    public List<UtilisationCreditDto> getByCertificat(
            @PathVariable Long certificatCreditId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findByCertificatCreditId(certificatCreditId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit')")
    public UtilisationCreditDto create(@Valid @RequestBody CreateUtilisationCreditRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit')")
    public UtilisationCreditDto update(
            @PathVariable Long id,
            @Valid @RequestBody CreateUtilisationCreditRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.update(id, request, user);
    }

    @PostMapping("/{id}/soumettre")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit')")
    public UtilisationCreditDto soumettreBrouillon(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.soumettreBrouillon(id, user);
    }

    /** Suppression définitive d'un brouillon uniquement. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.submit', 'utilisation.interieur.submit')")
    public void deleteBrouillon(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteBrouillon(id, user);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.verify', 'utilisation.douane.dgd.quittance.visa', 'utilisation.douane.dgd.reject', 'utilisation.douane.dgtcp.impute', 'utilisation.douane.dgtcp.solde.update', 'utilisation.interieur.dgtcp.verify', 'utilisation.interieur.dgtcp.validate', 'utilisation.interieur.dgtcp.solde.update', 'utilisation.interieur.dgtcp.reject')")
    public UtilisationCreditDto updateStatut(
            @PathVariable Long id,
            @RequestParam StatutUtilisation statut,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateStatut(id, statut, user);
    }

    @PostMapping("/{id}/apurement-tva")
    @PreAuthorize("hasAnyAuthority('utilisation.interieur.dgtcp.solde.update')")
    public UtilisationCreditDto apurerTVAInterieure(
            @PathVariable Long id,
            @Valid @RequestBody ApurerTVAInterieureRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.apurerTVAInterieure(id, request, user);
    }

    /**
     * Étape DGD : annotation des lignes du bulletin (AU_CI / A_PAYER).
     * Accepte multipart/form-data :
     *   - {@code decisions} : JSON array stringifié ({ligneId, affectation, valeurTaxe?})
     *   - {@code file}      : justificatif bulletin annoté (optionnel, type BULLETIN_ANNOTE)
     * Le DGD peut également modifier les valeurs des lignes via {@code valeurTaxe}.
     * Appelable depuis DEMANDEE (premier visa) et EN_CONTROLE_DGD (re-annotation).
     * Aucune opération financière. Statut résultant : EN_CONTROLE_DGD.
     */
    @PostMapping(value = "/{id}/visa-dgd", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.quittance.visa')")
    public UtilisationCreditDto visaDgd(
            @PathVariable Long id,
            @RequestParam("decisions") String decisionsJson,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return service.visaDgd(id, decisionsJson, file, user);
    }

    /**
     * Étape Entreprise : saisie du chèque certifié après visa DGD.
     * Accepte multipart/form-data : champs du chèque + fichier justificatif obligatoire.
     * Statut résultant : CHEQUE_SAISI.
     */
    @PostMapping(value = "/{id}/cheque", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.entreprise.cheque')")
    public UtilisationCreditDto saisirCheque(
            @PathVariable Long id,
            @RequestParam("banqueNom") String banqueNom,
            @RequestParam("numeroCheque") String numeroCheque,
            @RequestParam("montantCheque") java.math.BigDecimal montantCheque,
            @RequestParam(value = "dateCheque", required = false) String dateCheque,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        SaisirChequeRequest request = SaisirChequeRequest.builder()
                .banqueNom(banqueNom)
                .numeroCheque(numeroCheque)
                .montantCheque(montantCheque)
                .dateCheque(dateCheque != null && !dateCheque.isBlank()
                        ? java.time.Instant.parse(dateCheque) : null)
                .build();
        return service.saisirCheque(id, request, file, user);
    }

    /**
     * Étape DGTCP : validation du chèque et envoi au Trésor.
     * Statut résultant : ENVOYEE_AU_TRESOR.
     */
    @PostMapping("/{id}/envoyer-au-tresor")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.envoyer.tresor')")
    public UtilisationCreditDto envoyerAuTresor(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.envoyerAuTresor(id, user);
    }

    /**
     * Étape DGTCP : saisie des quittances Trésor + justificatifs (scan de chaque quittance).
     * Accepte multipart/form-data :
     *   - {@code quittances} : JSON array stringifié (liste des quittances)
     *   - {@code files}      : liste de fichiers indexés sur les quittances (files[0] → quittances[0])
     * Statut résultant : QUITTANCES_ENREGISTREES.
     */
    @PostMapping(value = "/{id}/quittances", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.quittances')")
    public UtilisationCreditDto saisirQuittances(
            @PathVariable Long id,
            @RequestParam("quittances") String quittancesJson,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return service.saisirQuittances(id, quittancesJson, files, user);
    }

    /**
     * Étape DGTCP : génération du certificat d'utilisation + débit financier.
     * Débite le solde cordon (hors TVA), décrémente le quota TVA importation,
     * alimente le stock TVA déductible. Statut résultant : LIQUIDEE.
     */
    @PostMapping("/{id}/liquidation-douane")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgtcp.impute', 'utilisation.douane.dgtcp.solde.update')")
    public UtilisationCreditDto liquiderDouane(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.liquiderDouane(id, user);
    }

    /**
     * Étape Entreprise : accusé de réception du certificat d'utilisation.
     * Statut résultant : CLOTUREE.
     */
    @PostMapping("/{id}/cloturer-reception")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.entreprise.reception')")
    public UtilisationCreditDto cloturerReception(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.cloturerReceptionEntreprise(id, user);
    }

    /**
     * Retourne les lignes du bulletin pour une utilisation douanière.
     * Accessible par tous les acteurs autorisés à voir la demande.
     */
    @GetMapping("/{id}/lignes-bulletin")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.douane.history.view', 'utilisation.ac.view', 'archivage.view')")
    public List<LigneBulletinDto> getLignesBulletin(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.findById(id, user);
        return ligneBulletinRepository.findByUtilisationDouaniere_IdOrderByTypeLigneAscIdAsc(id)
                .stream()
                .map(l -> LigneBulletinDto.builder()
                        .id(l.getId())
                        .codeTaxe(l.getCodeTaxe())
                        .denominationTaxe(l.getDenominationTaxe())
                        .typeLigne(l.getTypeLigne())
                        .valeurTaxe(l.getValeurTaxe())
                        .affectation(l.getAffectation())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Retourne les quittances Trésor enregistrées pour une utilisation douanière.
     */
    @GetMapping("/{id}/quittances")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.douane.solde.view', 'utilisation.douane.history.view', 'utilisation.ac.view', 'archivage.view')")
    public List<QuittanceTresorDto> getQuittances(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.getQuittances(id, user);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('utilisation.douane.dgd.queue.view', 'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', 'utilisation.interieur.dgi.view', 'utilisation.douane.solde.view', 'utilisation.interieur.solde.view', 'utilisation.ac.view', 'archivage.view')")
    public List<DocumentUtilisationCreditDto> getDocuments(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.findById(id, user);
        return documentService.findByUtilisationCreditId(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.document.upload', 'utilisation.interieur.document.upload')")
    public DocumentUtilisationCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam TypeDocument type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.upload(id, type, message, file, user);
    }
}
