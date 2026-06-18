package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import org.springframework.stereotype.Component;

/**
 * Chemins front pour deep-link depuis les notifications (cloche / WebSocket).
 */
@Component
public class NotificationNavigationHelper {

    public String buildRedirectPath(String entityType, Long entityId, Long certificatCreditId) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        return switch (entityType) {
            case "DemandeCorrection" -> entityId != null ? "/dashboard/demandes/" + entityId : null;
            case "CertificatCredit" -> entityId != null ? "/dashboard/certificats/" + entityId : null;
            case "UtilisationCredit" -> entityId != null ? "/dashboard/utilisations/" + entityId : null;
            case "TransfertCredit" -> entityId != null ? "/dashboard/transferts/" + entityId : null;
            case "ClotureCredit" -> certificatCreditId != null
                    ? "/dashboard/certificats/" + certificatCreditId
                    : null;
            default -> null;
        };
    }

    public String buildRedirectPath(ContexteExplication contexte, Long dossierId) {
        if (contexte == null || dossierId == null) {
            return null;
        }
        return switch (contexte) {
            case CORRECTION -> "/dashboard/demandes/" + dossierId;
            case CERTIFICAT -> "/dashboard/certificats/" + dossierId;
            case UTILISATION -> "/dashboard/utilisations/" + dossierId;
        };
    }
}
