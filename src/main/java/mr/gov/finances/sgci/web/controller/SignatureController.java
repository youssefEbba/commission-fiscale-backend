package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.SignatureService;
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
 * Gestion des images de signature (PNG) utilisées pour les documents générés côté client (jsPDF).
 * Lecture ouverte à tout utilisateur authentifié ; écriture réservée à ADMIN_SI (toute
 * signature) ou au titulaire de sa propre signature (voir {@link SignatureService#create}).
 */
@RestController
@RequestMapping("/api/signatures")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SignatureController {

    private final SignatureService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SignatureDto> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return service.list(role, utilisateurId, activeOnly);
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public SignatureDto getActive(
            @RequestParam Role role,
            @RequestParam(required = false) Long utilisateurId
    ) {
        return service.getActive(role, utilisateurId);
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getContent(@PathVariable Long id) {
        byte[] content = service.getContent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"signature-" + id + ".png\"")
                .body(content);
    }

    @GetMapping("/{id}/base64")
    @PreAuthorize("isAuthenticated()")
    public SignatureBase64Dto getBase64(@PathVariable Long id) {
        return service.getBase64(id);
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(file, role, utilisateurId, nomAffiche, activer, user);
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
