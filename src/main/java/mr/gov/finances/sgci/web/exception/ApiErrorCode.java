package mr.gov.finances.sgci.web.exception;

/**
 * Codes d’erreur stables exposés dans {@link ErrorResponse#code()} (contrat API).
 * Le front peut faire un switch sur ces constantes ; le libellé reste dans {@code message}.
 */
public final class ApiErrorCode {

    private ApiErrorCode() {
    }

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ROLE_FORBIDDEN = "ROLE_FORBIDDEN";
    public static final String CONFLICT = "CONFLICT";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final String WORKFLOW_TRANSITION_INVALID = "WORKFLOW_TRANSITION_INVALID";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String STORAGE_UPLOAD_FAILED = "STORAGE_UPLOAD_FAILED";
    /** MinIO / S3 indisponible ou mal configuré (HTTP 503). */
    public static final String OBJECT_STORAGE_UNAVAILABLE = "OBJECT_STORAGE_UNAVAILABLE";
    /** API de change (open.er-api, exchangerate.host, etc.) injoignable ou en erreur (HTTP 503). */
    public static final String EXTERNAL_EXCHANGE_SERVICE_UNAVAILABLE = "EXTERNAL_EXCHANGE_SERVICE_UNAVAILABLE";

    /** Marché déjà lié à une demande de correction non annulée. */
    public static final String MARCHE_DEMANDE_ACTIVE = "MARCHE_DEMANDE_ACTIVE";
}
