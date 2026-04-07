package mr.gov.finances.sgci.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import mr.gov.finances.sgci.workflow.WorkflowTransitionException;

import java.util.stream.Collectors;

@RestControllerAdvice
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

    /**
     * Erreurs métier historiques : HTTP 400 + code générique.
     * Préférer {@link ApiException} avec un code plus fin lors des évolutions.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.BUSINESS_RULE_VIOLATION,
                ex.getMessage() != null ? ex.getMessage() : ApiErrorCode.BUSINESS_RULE_VIOLATION,
                null));
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
