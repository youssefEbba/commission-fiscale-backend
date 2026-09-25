package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.service.MinioService;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Téléchargement d'un justificatif rattaché à une demande d'utilisation.
 *
 * <p>Portée volontairement limitée aux documents d'utilisation : c'est le seul identifiant exposé
 * par l'API sous le nom {@code documentId} (quittance DGI notamment). Les autres familles de
 * documents ont leurs propres identifiants, qui se chevauchent avec ceux-ci.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentDownloadController {

    private final DocumentUtilisationCreditRepository documentRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EffectiveIdentityService effectiveIdentityService;
    private final MinioService minioService;

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long documentId,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        DocumentUtilisationCredit doc = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Document non trouvé: " + documentId));
        assertPeutTelecharger(doc, user);

        byte[] contenu = minioService.downloadFile(doc.getChemin());
        String nom = doc.getNomFichier() != null ? doc.getNomFichier() : "document";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nom).build().toString())
                .contentType(typeDeContenu(nom))
                .body(new ByteArrayResource(contenu));
    }

    /**
     * Les administrations concernées accèdent à tout justificatif ; une entreprise n'accède qu'aux
     * siens, qu'elle soit demanderesse ou titulaire du certificat.
     */
    private void assertPeutTelecharger(DocumentUtilisationCredit doc, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Role role = user.getRole();
        if (role == Role.DGI || role == Role.DGD || role == Role.DGTCP
                || role == Role.PRESIDENT || role == Role.ADMIN_SI) {
            return;
        }
        if (role != Role.ENTREPRISE && role != Role.SOUS_TRAITANT && role != Role.COMMISSION_RELAIS) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé au justificatif");
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Long mienne = effectiveIdentityService.resolveEntrepriseId(user, u);
        UtilisationCredit utilisation = doc.getUtilisationCredit();
        Long demandeur = utilisation != null && utilisation.getEntreprise() != null
                ? utilisation.getEntreprise().getId() : null;
        Long titulaire = utilisation != null && utilisation.getCertificatCredit() != null
                && utilisation.getCertificatCredit().getEntreprise() != null
                ? utilisation.getCertificatCredit().getEntreprise().getId() : null;
        if (mienne == null || (!mienne.equals(demandeur) && !mienne.equals(titulaire))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Justificatif hors périmètre");
        }
    }

    private MediaType typeDeContenu(String nom) {
        String n = nom.toLowerCase();
        if (n.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (n.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
