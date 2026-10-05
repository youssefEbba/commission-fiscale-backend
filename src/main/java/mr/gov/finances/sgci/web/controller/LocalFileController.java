package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentVisibilitePolicy;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Téléchargement des fichiers stockés en mode {@code app.storage.backend=local}.
 *
 * <p>Cette route ne reçoit qu'un nom de fichier : jusqu'ici elle servait donc n'importe quel
 * document à n'importe quel compte connecté, ce qui en faisait un contournement de tout masquage
 * posé ailleurs — et une fuite entre entreprises. Elle résout désormais le fichier vers la pièce
 * d'utilisation qui le porte et applique la même règle que les autres chemins de lecture.
 *
 * <p><b>Limite assumée</b> : un fichier qui n'appartient à aucune pièce d'utilisation — ceux des
 * autres familles documentaires — reste servi à tout compte authentifié. Fermer ces familles
 * demande de les traiter chacune, ce qui sort de ce lot.
 */
@RestController
@RequestMapping("/api/local-files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LocalFileController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final DocumentUtilisationCreditRepository documentUtilisationCreditRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EffectiveIdentityService effectiveIdentityService;

    @GetMapping("/{fileName:[a-zA-Z0-9._-]+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable String fileName,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        assertPeutLire(fileName, user);

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("documents");
        Path file = base.resolve(fileName).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Même règle que {@code DocumentDownloadController} : périmètre de l'entreprise, puis
     * communicabilité de la pièce.
     */
    private void assertPeutLire(String fileName, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Role role = user.getRole();
        if (DocumentVisibilitePolicy.acteurVoitTout(role)) {
            return;
        }
        Optional<DocumentUtilisationCredit> piece =
                documentUtilisationCreditRepository.findFirstByCheminEndsWith("/" + fileName);
        if (piece.isEmpty()) {
            return;   // fichier d'une autre famille documentaire : hors du périmètre de ce contrôle
        }
        DocumentUtilisationCredit doc = piece.get();
        UtilisationCredit utilisation = doc.getUtilisationCredit();

        if (role != Role.ENTREPRISE && role != Role.SOUS_TRAITANT && role != Role.COMMISSION_RELAIS) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé au justificatif");
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Long mienne = effectiveIdentityService.resolveEntrepriseId(user, u);
        Long demandeur = utilisation != null && utilisation.getEntreprise() != null
                ? utilisation.getEntreprise().getId() : null;
        Long titulaire = utilisation != null && utilisation.getCertificatCredit() != null
                && utilisation.getCertificatCredit().getEntreprise() != null
                ? utilisation.getCertificatCredit().getEntreprise().getId() : null;
        if (mienne == null || (!mienne.equals(demandeur) && !mienne.equals(titulaire))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Justificatif hors périmètre");
        }
        if (!DocumentVisibilitePolicy.visiblePour(doc.getCodeDocument(),
                utilisation != null ? utilisation.getStatut() : null, role)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED,
                    "Pièce produite par l'administration : elle sera communicable après la liquidation du dossier");
        }
    }
}
