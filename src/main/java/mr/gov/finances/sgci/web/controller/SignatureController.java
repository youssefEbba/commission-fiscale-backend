package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEmpreinte;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.SignatureService;
import mr.gov.finances.sgci.web.dto.MesEmpreintesDto;
import mr.gov.finances.sgci.web.dto.SignatureBase64Dto;
import mr.gov.finances.sgci.web.dto.SignatureDto;
import mr.gov.finances.sgci.web.dto.UpdateSignatureRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Empreintes PNG — signature manuscrite et cachet — apposées sur les documents officiels générés
 * côté client (jsPDF).
 *
 * <p>Le paramètre {@code type} est partout optionnel : absent, il vaut {@code SIGNATURE} là où une
 * empreinte unique doit être résolue, et « toutes natures » sur la liste. Les clients antérieurs à
 * l'introduction du cachet restent donc valides sans modification.
 *
 * <p>Écriture réservée à ADMIN_SI (toute empreinte) ou au titulaire pour la sienne
 * ({@code SignatureService.assertCanManage}). <b>Lecture restreinte au titulaire, à
 * l'administrateur, et à l'empreinte générique de son propre rôle</b> : une signature manuscrite
 * n'a pas à être téléchargeable par n'importe quel compte authentifié. Une empreinte hors périmètre
 * répond 404, et non 403, pour ne pas offrir d'oracle d'énumération.
 */
@RestController
@RequestMapping("/api/signatures")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SignatureController {

    private final SignatureService service;

    /** {@code type} absent = toutes natures. Filtré par droit de lecture. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SignatureDto> list(
            @RequestParam(required = false) TypeEmpreinte type,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) Boolean activeOnly,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.list(type, role, utilisateurId, activeOnly, user);
    }

    /**
     * Empreinte active, avec repli sur celle du rôle : une empreinte personnelle l'emporte, à défaut
     * l'empreinte générique ({@code utilisateurId} nul en base) prend le relais.
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public SignatureDto getActive(
            @RequestParam Role role,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) TypeEmpreinte type,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.getActive(type, role, utilisateurId, user);
    }

    /**
     * Signature et cachet de l'utilisateur courant en un seul appel — l'écran de profil et l'écran
     * de signature n'ont ainsi pas à connaître leur propre identifiant.
     *
     * <p>{@code withContent=true} inline les deux images en data URL, pour la composition jsPDF. Par
     * défaut elles sont omises : un PNG de 1 Mo pèse environ 1,3 Mo encodé.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MesEmpreintesDto mesEmpreintes(
            @RequestParam(required = false, defaultValue = "false") boolean withContent,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.mesEmpreintes(user, withContent);
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getContent(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        byte[] content = service.getContent(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"empreinte-" + id + ".png\"")
                .body(content);
    }

    @GetMapping("/{id}/base64")
    @PreAuthorize("isAuthenticated()")
    public SignatureBase64Dto getBase64(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getBase64(id, user);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('signature.manage')")
    public SignatureDto create(
            @RequestParam("file") MultipartFile file,
            @RequestParam Role role,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) String nomAffiche,
            @RequestParam(required = false) Boolean activer,
            @RequestParam(required = false) TypeEmpreinte type,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(file, type, role, utilisateurId, nomAffiche, activer, user);
    }

    /** Dépôt pour soi : rôle et identifiant déduits du jeton, l'empreinte est activée d'office. */
    @PostMapping(value = "/me", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('signature.manage')")
    public SignatureDto createPourMoi(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) TypeEmpreinte type,
            @RequestParam(required = false) String nomAffiche,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.createPourMoi(file, type, nomAffiche, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('signature.manage')")
    public SignatureDto updateMetadata(
            @PathVariable Long id,
            @RequestBody UpdateSignatureRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateMetadata(id, request, user);
    }

    /** Nouvelle version de la même empreinte : le type est repris de la version remplacée. */
    @PostMapping(value = "/{id}/remplacer", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('signature.manage')")
    public SignatureDto remplacer(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.remplacer(id, file, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('signature.manage')")
    public void deactivate(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.deactivate(id, user);
    }
}
