package mr.gov.finances.sgci.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.archive.ImportArchiveLotService;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.AffectationRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDetailDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.ImportRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.ImportResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.LotDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Reprise d'archive par fichier de période : un export qui regroupe tous les crédits d'une période.
 * Même réserve que l'import d'un relevé à un crédit : {@code archive.import}.
 */
@RestController
@RequestMapping("/api/archive/lots")
@RequiredArgsConstructor
@Tag(name = "Archive", description = "Reprise des crédits d'impôt de l'ancienne application")
public class ImportArchiveLotController {

    private final ImportArchiveLotService service;

    /** Lit le fichier et enregistre le lot ; le même fichier versé deux fois rend le lot existant. */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Analyser un export de période et l'enregistrer en lot")
    public LotDto analyser(@RequestParam("fichier") MultipartFile fichier,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        return service.analyser(fichier, user);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Lots de reprise, du plus récent au plus ancien")
    public List<LotDto> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Un lot et ses crédits")
    public LotDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/credits/{creditId}")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Un crédit du lot avec son relevé complet")
    public CreditDetailDto credit(@PathVariable Long id, @PathVariable Long creditId) {
        return service.credit(id, creditId);
    }

    @PatchMapping("/{id}/credits")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Affecter entreprise, autorité, convention ou marché à des crédits du lot")
    public LotDto affecter(@PathVariable Long id, @RequestBody AffectationRequest request) {
        return service.affecter(id, request);
    }

    @PostMapping("/{id}/rapprocher")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Relancer les rapprochements sur les choix encore vides")
    public LotDto rapprocher(@PathVariable Long id) {
        return service.rapprocher(id);
    }

    @PostMapping("/{id}/dgi")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Interroger la DGI pour les NIF sans entreprise (par paquets)")
    public LotDto interrogerDgi(@PathVariable Long id) {
        return service.interrogerDgi(id);
    }

    @PostMapping("/{id}/importer")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Importer les crédits prêts du lot, chacun dans sa propre transaction")
    public ImportResultatDto importer(@PathVariable Long id, @RequestBody(required = false) ImportRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return service.importer(id, request != null ? request.getCreditIds() : null, user);
    }

    /**
     * Réapplique aux crédits déjà importés les montants recalculés (composantes du crédit douanier,
     * soldes, format des utilisations). Pour un lot importé avant le 03/11/2026, déposer d'abord à
     * nouveau le même fichier, qui est alors relu.
     */
    @PostMapping("/{id}/recalculer")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Recalculer les crédits déjà importés du lot")
    public ImportResultatDto recalculer(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.recalculer(id, user);
    }

    /** Retire le lot ; les crédits déjà importés restent. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('archive.import')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un lot (les crédits importés restent)")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}
