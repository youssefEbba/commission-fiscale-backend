package mr.gov.finances.sgci.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.archive.ImportArchiveCreditService;
import mr.gov.finances.sgci.web.dto.archive.ImportArchiveResultatDto;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Reprise des relevés de crédit d'impôt de l'ancienne application.
 *
 * <p>Opération d'administration : elle inscrit directement des soldes en base, sans passer par le
 * circuit de visas. Elle est donc réservée au profil disposant de {@code archive.import}.
 */
@RestController
@RequestMapping("/api/archive/credits")
@RequiredArgsConstructor
@Tag(name = "Archive", description = "Reprise des crédits d'impôt de l'ancienne application")
public class ImportArchiveCreditController {

    private final ImportArchiveCreditService service;

    /** Lit le relevé et renvoie ce qui serait créé, sans rien écrire. */
    @PostMapping(value = "/previsualiser", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Aperçu d'un relevé d'archive, sans écriture en base")
    public ReleveArchiveDto previsualiser(@RequestParam("fichier") MultipartFile fichier) {
        return service.previsualiser(fichier);
    }

    /** Crée le certificat et ses utilisations à partir du relevé. */
    @PostMapping(value = "/importer", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Import d'un relevé d'archive vers un certificat de crédit")
    public ImportArchiveResultatDto importer(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("entrepriseId") Long entrepriseId,
            @RequestParam("autoriteContractanteId") Long autoriteContractanteId,
            @RequestParam("conventionId") Long conventionId,
            @RequestParam(value = "marcheId", required = false) Long marcheId,
            @RequestParam(value = "confirmerMalgreAnomalies", defaultValue = "false") boolean confirmer,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.importer(fichier, entrepriseId, autoriteContractanteId, conventionId, marcheId,
                confirmer, user);
    }

    /** Codes de taxe du référentiel, pour information à l'écran. */
    @GetMapping("/codes-taxe")
    @PreAuthorize("hasAuthority('archive.import')")
    @Operation(summary = "Codes de taxe reconnus lors de la lecture d'un relevé")
    public List<String> codesTaxe() {
        return service.codesTaxeConnus();
    }
}
