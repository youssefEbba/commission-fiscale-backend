package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.LigneBulletinLiquidationRepository;
import mr.gov.finances.sgci.domain.enums.ModeApposition;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.AdminCorrectionUtilisationRequest;
import mr.gov.finances.sgci.web.dto.ApurerTVAInterieureRequest;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEmissionDto;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LigneBulletinDto;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
import mr.gov.finances.sgci.web.dto.QuittanceTresorDto;
import mr.gov.finances.sgci.web.dto.SaisirChequeRequest;
import mr.gov.finances.sgci.web.dto.SaisirQuittancesRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;

import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
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
     * Étape DGD : valide ou modifie les annotations entreprise (AU_CI / A_PAYER) sur chaque ligne.
     * Accepte multipart/form-data :
     *   - {@code decisions} : JSON array ({ligneId, affectation?, valeurTaxe?}) — si {@code affectation} est omis
     *     sur une ligne avec montant &gt; 0, la proposition {@code affectationEntreprise} est retenue (validation).
     *   - {@code file}      : bulletin annoté (optionnel, BULLETIN_ANNOTE)
     * L'entreprise renseigne d'abord {@code affectation} sur chaque ligne à la création (voir POST/PUT utilisation).
     * Appelable depuis DEMANDEE et EN_CONTROLE_DGD (re-annotation). Statut résultant : EN_CONTROLE_DGD.
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
     * Étape DGTCP : contrôle du dossier et transmission au Président.
     * <p>
     * La DGTCP atteste que le bulletin est visé par la DGD et le chèque certifié saisi, puis
     * présente le dossier au Président qui émettra le certificat. Idempotent : un second appel
     * renvoie le même statut. Statut résultant : TRANSMISE_AU_PRESIDENT.
     */
    @PostMapping("/{id}/transmission-president")
    @PreAuthorize("hasAuthority('utilisation.douane.dgtcp.transmettre.president')")
    public UtilisationCreditDto transmettreAuPresident(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.transmettreAuPresident(id, user);
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
     * Étape DGI : dépôt de la quittance attestant le paiement de la TVA intérieure.
     *
     * <p>S'intercale entre la validation DGTCP et l'apurement. Le dépôt est idempotent : un second
     * appel remplace la quittance précédente. Le justificatif (PDF, PNG ou JPG) est obligatoire au
     * premier dépôt, facultatif ensuite. Statut résultant : QUITTANCE_DGI_ENREGISTREE.
     */
    @PostMapping(value = "/{id}/quittance-dgi", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('utilisation.interieur.dgi.quittance', 'utilisation.admin_override')")
    public UtilisationCreditDto deposerQuittanceDgi(
            @PathVariable Long id,
            @RequestParam("numeroQuittance") String numeroQuittance,
            @RequestParam(value = "dateQuittance", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateQuittance,
            @RequestParam("montant") BigDecimal montant,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return service.deposerQuittanceDgi(id, numeroQuittance, dateQuittance, montant, file, user);
    }

    /**
     * Étape DGTCP : débit financier de la liquidation douanière.
     * Débite le solde cordon (hors TVA), décrémente le quota TVA importation,
     * alimente le stock TVA déductible. Statut résultant : LIQUIDEE.
     * <p>
     * Le certificat d'utilisation n'est pas produit ici : il est émis ensuite par le Président
     * via {@code POST /{id}/certificat-utilisation}.
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
     * Étape Président : émission du certificat d'utilisation, pour les deux branches.
     * <p>
     * Attribue le numéro {@code CU-nnn/AAAA} et fait passer la demande en CERTIFICAT_EMIS. Le calcul
     * de la DGTCP doit être acquis : LIQUIDEE en douane, APUREE en TVA intérieure. Idempotent — un
     * second appel renvoie le même numéro sans erreur.
     */
    @PostMapping("/{id}/certificat-utilisation")
    @PreAuthorize("hasAuthority('utilisation.president.certificat.emettre')")
    public UtilisationCreditDto emettreCertificatUtilisation(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.emettreCertificatUtilisation(id, user);
    }

    /**
     * Substitution administrative : l'ADMIN_SI émet à la place du Président, motif obligatoire.
     * <p>
     * Le fichier est optionnel et déposé après la numérotation — il doit porter le numéro que cet
     * appel attribue. Il passe par cette route parce que l'ADMIN_SI ne détient pas les permissions
     * de {@code POST /{id}/documents}.
     */
    @PostMapping(value = "/{id}/certificat-utilisation/admin", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('utilisation.certificat.admin_override')")
    public UtilisationCreditDto adminEmettreCertificatUtilisation(
            @PathVariable Long id,
            @RequestParam("motif") String motif,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return service.adminEmettreCertificatUtilisation(id, motif, file, user);
    }

    /** État d'émission : bouton actif ou non, et code de blocage stable côté front. */
    @GetMapping("/{id}/certificat-utilisation/etat")
    @PreAuthorize("hasAnyAuthority('utilisation.president.certificat.emettre', "
            + "'utilisation.certificat.admin_override', "
            + "'utilisation.douane.dgtcp.queue.view', 'utilisation.interieur.dgtcp.queue.view', "
            + "'utilisation.douane.solde.view', 'utilisation.interieur.solde.view')")
    public CertificatUtilisationEmissionDto etatEmissionCertificat(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.etatEmissionCertificat(id, user);
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
        return documentService.findByUtilisationCreditId(id, user);
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('utilisation.douane.document.upload', 'utilisation.interieur.document.upload')")
    public DocumentUtilisationCreditDto uploadDocument(
            @PathVariable Long id,
            @RequestParam(value = "codeDocument", required = false) String codeDocument,
            @RequestParam(value = "typeDocument", required = false) String typeDocument,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file,
            // Chemin choisi par le Président : scan signé à la main, ou empreintes apposées par le
            // système. Optionnel — les pièces sans signature ne le renseignent pas.
            @RequestParam(value = "modeApposition", required = false) ModeApposition modeApposition,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        String resolved = mr.gov.finances.sgci.web.support.DocumentUploadParamResolver
                .resolveCodeDocument(codeDocument, typeDocument, type);
        return documentService.upload(id, resolved, message, file, modeApposition, user);
    }

    /** Correction administrateur d'informations, à tout moment (ADMIN_SI, motif obligatoire). */
    @PatchMapping("/{id}/admin-correction")
    @PreAuthorize("hasAuthority('utilisation.admin_override')")
    public UtilisationCreditDto adminCorrectInfo(
            @PathVariable Long id,
            @RequestParam String motif,
            @RequestBody AdminCorrectionUtilisationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.adminCorrectInfo(id, request, motif, user);
    }

    /** Remplacement administrateur d'un document, à tout moment (ADMIN_SI, motif obligatoire). */
    @PostMapping(value = "/{id}/documents/admin-correction", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('utilisation.admin_override')")
    public DocumentUtilisationCreditDto adminReplaceDocument(
            @PathVariable Long id,
            @RequestParam String codeDocument,
            @RequestParam String motif,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) throws IOException {
        return documentService.adminReplace(id, codeDocument, motif, file, user);
    }
}
