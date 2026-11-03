package mr.gov.finances.sgci.service.document;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.service.DocumentRequirementValidator;
import mr.gov.finances.sgci.service.MinioService;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Séquence commune des dépôts GED : valider la pièce paramétrée, incrémenter la version,
 * envoyer le fichier vers MinIO.
 */
@Component
@RequiredArgsConstructor
public class DocumentUploadSupport {

    private final DocumentRequirementValidator requirementValidator;
    private final MinioService minioService;

    public record StoredUpload(String fileUrl, String nomFichier, long taille, int version) {}

    public void assertNonVide(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
    }

    public int prochaineVersion(Integer versionPrecedente) {
        return versionPrecedente != null ? versionPrecedente + 1 : 1;
    }

    public StoredUpload validerEtStocker(ProcessusDocument processus, String codeDocument,
                                         MultipartFile file, Integer versionPrecedente) throws IOException {
        assertNonVide(file);
        requirementValidator.validateUpload(processus, codeDocument, file);
        int version = versionPrecedente == null ? 1 : prochaineVersion(versionPrecedente);
        String original = file.getOriginalFilename();
        return new StoredUpload(
                minioService.uploadFile(file),
                original != null ? original : file.getName(),
                file.getSize(),
                version);
    }
}
