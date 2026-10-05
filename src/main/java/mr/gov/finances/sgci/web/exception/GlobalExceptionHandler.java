package mr.gov.finances.sgci.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import mr.gov.finances.sgci.workflow.WorkflowTransitionException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static ResponseEntity<ErrorResponse> body(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return body(ErrorResponse.of(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return body(ErrorResponse.of(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ApiErrorCode.FILE_TOO_LARGE,
                "Fichier trop volumineux",
                null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return body(ErrorResponse.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Type de contenu non supporté. Attendu: " + ex.getSupportedMediaTypes(),
                null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Corps de requête illisible ou format JSON invalide",
                null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Paramètre obligatoire manquant: " + ex.getParameterName(),
                null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Valeur invalide pour le paramètre '" + ex.getName() + "': " + ex.getValue(),
                null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Erreur de traitement du fichier uploadé: " + ex.getMessage(),
                null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorCode.INVALID_CREDENTIALS,
                ex.getMessage() != null ? ex.getMessage() : "Identifiants invalides",
                null));
    }

    @ExceptionHandler({InsufficientAuthenticationException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthMissing(RuntimeException ex) {
        return body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorCode.AUTH_REQUIRED,
                "Non authentifié",
                null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                ApiErrorCode.ACCESS_DENIED,
                "Accès refusé",
                null));
    }

    @ExceptionHandler(WorkflowTransitionException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowTransition(WorkflowTransitionException ex) {
        return body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                ex.getCode() != null ? ex.getCode() : WorkflowTransitionException.DEFAULT_CODE,
                ex.getMessage(),
                null));
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedRollback(UnexpectedRollbackException ex) {
        ResponseEntity<ErrorResponse> mapped = mapKnownCause(ex);
        if (mapped != null) {
            return mapped;
        }
        log.warn("Rollback transactionnel sans cause métier explicite — cause racine : {}",
                rootCauseMessage(ex), ex);
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.BUSINESS_RULE_VIOLATION,
                "Opération annulée (incohérence transactionnelle). Vérifiez les données ou réessayez.",
                null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        ResponseEntity<ErrorResponse> mapped = mapKnownCause(ex);
        if (mapped != null) {
            return mapped;
        }
        log.warn("Violation contrainte base de données", ex);
        return body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                ApiErrorCode.CONFLICT,
                "Conflit de données (référence ou contrainte unique). Vérifiez les identifiants et numéros saisis.",
                null));
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static ResponseEntity<ErrorResponse> mapKnownCause(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ApiException api) {
                return body(ErrorResponse.of(api.getStatus(), api.getCode(), api.getMessage(), api.getDetails()));
            }
            if (cause instanceof DataIntegrityViolationException dive) {
                // Le message du driver nomme la table, la colonne et parfois la requête entière :
                // il appartient au journal, pas à la réponse HTTP.
                log.warn("Violation de contrainte base de données : {}",
                        dive.getMostSpecificCause() != null
                                ? dive.getMostSpecificCause().getMessage()
                                : dive.getMessage());
                return body(ErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        ApiErrorCode.CONFLICT,
                        "Conflit de données (référence ou contrainte unique).",
                        null));
            }
            cause = cause.getCause();
        }
        return null;
    }

    /**
     * Erreurs métier historiques : HTTP 400 + code générique.
     * Préférer {@link ApiException} avec un code plus fin lors des évolutions.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        if (ex instanceof UnexpectedRollbackException ur) {
            return handleUnexpectedRollback(ur);
        }
        // Ce chemin renvoie le message de l'exception, parce qu'il porte les erreurs métier
        // historiques, écrites pour l'utilisateur. Une panne technique n'en est pas une : son
        // message est celui du driver ou d'Hibernate, et contient la requête SQL complète.
        if (estDOrigineTechnique(ex)) {
            log.error("Panne technique remontée à l'API", ex);
            return body(ErrorResponse.of(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    ApiErrorCode.INTERNAL_ERROR,
                    "Opération impossible : incident technique. L'administrateur a été notifié "
                            + "par le journal du serveur.",
                    null));
        }
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.BUSINESS_RULE_VIOLATION,
                ex.getMessage() != null ? ex.getMessage() : ApiErrorCode.BUSINESS_RULE_VIOLATION,
                null));
    }

    /**
     * Vrai si l'exception vient de la couche de persistance plutôt que d'une règle métier.
     *
     * <p>Reconnaître la panne à son origine, et non à son message, évite d'avoir à deviner
     * quelles formules un driver peut produire : {@code SQLException}, les exceptions
     * {@code org.hibernate.*} et les {@code DataAccessException} de Spring sont techniques par
     * construction.
     */
    static boolean estDOrigineTechnique(Throwable ex) {
        for (Throwable cause = ex; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException
                    || cause instanceof org.springframework.dao.DataAccessException
                    || cause.getClass().getName().startsWith("org.hibernate.")
                    || cause.getClass().getName().startsWith("jakarta.persistence.")) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Validation échouée",
                details.isEmpty() ? null : details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex) {
        return body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorCode.INTERNAL_ERROR,
                "Erreur interne",
                null));
    }
}
