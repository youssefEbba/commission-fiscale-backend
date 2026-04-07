package mr.gov.finances.sgci.web.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier ou HTTP explicite : code stable pour le front, message lisible pour l’humain.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Object details;

    public ApiException(int status, String code, String message, Object details, Throwable cause) {
        super(message != null ? message : code, cause);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public ApiException(int status, String code, String message, Object details) {
        this(status, code, message, details, null);
    }

    public ApiException(int status, String code, String message) {
        this(status, code, message, null, null);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST.value(), code, message);
    }

    public static ApiException badRequest(String code, String message, Object details) {
        return new ApiException(HttpStatus.BAD_REQUEST.value(), code, message, details);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED.value(), code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN.value(), code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND.value(), code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT.value(), code, message);
    }

    public static ApiException conflict(String code, String message, Object details) {
        return new ApiException(HttpStatus.CONFLICT.value(), code, message, details);
    }

    /** Erreur technique non prévue (éviter pour les règles métier). */
    public static ApiException internal(String code, String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), code, message);
    }

    public static ApiException internal(String code, String message, Throwable cause) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), code, message, null, cause);
    }

    public static ApiException badRequest(String code, String message, Throwable cause) {
        return new ApiException(HttpStatus.BAD_REQUEST.value(), code, message, null, cause);
    }

    public static ApiException serviceUnavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE.value(), code, message);
    }

    public static ApiException serviceUnavailable(String code, String message, Throwable cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE.value(), code, message, null, cause);
    }
}
