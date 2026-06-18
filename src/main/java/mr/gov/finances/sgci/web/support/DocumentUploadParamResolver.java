package mr.gov.finances.sgci.web.support;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

/**
 * Résout le code document lors des uploads multipart.
 * <p>
 * Le backend canonique attend {@code codeDocument}. Le front historique envoie parfois
 * {@code type} (query ou form) ou {@code typeDocument} (réponses rejet temporaire).
 */
public final class DocumentUploadParamResolver {

    private DocumentUploadParamResolver() {
    }

    public static String resolveCodeDocument(String codeDocument, String typeDocument, String type) {
        if (codeDocument != null && !codeDocument.isBlank()) {
            return codeDocument.trim();
        }
        if (typeDocument != null && !typeDocument.isBlank()) {
            return typeDocument.trim();
        }
        if (type != null && !type.isBlank()) {
            return type.trim();
        }
        throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                "Paramètre obligatoire manquant: codeDocument (alias acceptés: type, typeDocument)");
    }
}
