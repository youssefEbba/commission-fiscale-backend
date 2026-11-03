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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import mr.gov.finances.sgci.workflow.WorkflowTransitionException;

import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static ResponseEntity<ErrorResponse> body(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        // Une erreur serveur (stockage indisponible, par exemple) doit laisser une trace : le reçu
        // de dépôt manquait sans que rien n'en dise la cause (anomalie A08).
        if (ex.getStatus() >= 500) {
            log.error("Erreur {} {} : {}", ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getCause() != null ? ex.getCause() : ex);
        }
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

    /**
     * Corps illisible. Quand c'est une valeur qui ne convient pas à son champ, le message nomme le
     * champ et ce qui est attendu — pour une énumération, la liste des valeurs acceptées. Sans cela,
     * une catégorie saisie en clair bloquait l'enregistrement d'une enquête sans dire pourquoi (A46).
     * La valeur saisie n'est pas renvoyée, comme pour les paramètres d'URL (handleTypeMismatch).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        JsonMappingException jme = cause(ex, JsonMappingException.class);
        if (jme != null && !jme.getPath().isEmpty()) {
            String champ = chemin(jme.getPath());
            Class<?> type = null;
            if (jme instanceof InvalidFormatException ife) {
                type = ife.getTargetType();
            } else if (jme instanceof ValueInstantiationException vie) {
                type = vie.getType() != null ? vie.getType().getRawClass() : null;
            } else if (jme instanceof MismatchedInputException mie) {
                type = mie.getTargetType();
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("champ", champ);
            String message;
            if (type != null && type.isEnum()) {
                List<String> acceptees = Arrays.stream(type.getEnumConstants())
                        .map(e -> ((Enum<?>) e).name())
                        .toList();
                details.put("valeursAcceptees", acceptees);
                message = "Valeur invalide pour « " + champ + " » : valeurs acceptées " + String.join(", ", acceptees);
            } else {
                message = "Valeur invalide pour « " + champ + " »" + attendu(type);
            }
            return body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ApiErrorCode.VALIDATION_FAILED, message, details));
        }
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Corps de requête illisible ou format JSON invalide",
                null));
    }

    private static <T extends Throwable> T cause(Throwable t, Class<T> type) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    /** « questions[0].categorie » à partir du chemin Jackson. */
    private static String chemin(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference r : path) {
            if (r.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(r.getFieldName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String attendu(Class<?> type) {
        if (type == null) {
            return "";
        }
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class)) {
            return " : nombre attendu";
        }
        if (type == Boolean.class || type == boolean.class) {
            return " : true ou false attendu";
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return type == LocalDate.class ? " : date attendue (AAAA-MM-JJ)" : " : date attendue (ISO 8601)";
        }
        return " : " + type.getSimpleName() + " attendu";
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
                "Valeur invalide pour le paramètre '" + ex.getName() + "'",
                null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
        // Le message de l'exception porte des chemins du serveur : il va au journal, pas à l'écran.
        log.warn("Téléversement illisible", ex);
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.VALIDATION_FAILED,
                "Le fichier n'a pas pu être lu : vérifiez qu'il n'est pas vide ni endommagé, puis réessayez.",
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
        if (ex instanceof IllegalStateException || ex instanceof IllegalArgumentException) {
            return body(ErrorResponse.of(
                    HttpStatus.BAD_REQUEST.value(),
                    ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    ex.getMessage() != null ? ex.getMessage() : ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    null));
        }
        log.error("RuntimeException inattendue", ex);
        return body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.BUSINESS_RULE_VIOLATION,
                "Opération refusée",
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

    /**
     * Une route qui n'existe pas rend 404, et non 500.
     *
     * <p>Sans ce gestionnaire, le filet {@code Exception.class} ci-dessous attrapait l'absence de
     * route et la rendait en « Erreur interne ». Conséquence concrète, constatée : une équipe
     * frontale a conclu qu'une route de suivi existait mais plantait, alors qu'elle n'avait jamais
     * été écrite — et a construit son écran en attendant une correction qui n'avait pas lieu
     * d'être. Un 500 dit « le serveur a échoué » ; un 404 dit « vous appelez autre chose ».
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleRouteInconnue(Exception ex) {
        return body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Route inconnue",
                null));
    }

    /**
     * Une route appelée avec la mauvaise méthode rend 405, et non 500.
     *
     * <p>Constaté en recette : le front envoyait POST sur une route PATCH, et l'écran ne recevait
     * qu'une « Erreur interne ». Le message donne les méthodes acceptées, pour que l'appel se
     * corrige du premier coup.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodeNonAutorisee(HttpRequestMethodNotSupportedException ex) {
        String acceptees = ex.getSupportedHttpMethods() == null ? ""
                : ex.getSupportedHttpMethods().stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
        ResponseEntity.BodyBuilder reponse = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) {
            reponse.allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return reponse.body(ErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "Méthode " + ex.getMethod() + " non acceptée sur cette route"
                        + (acceptees.isEmpty() ? "" : " (acceptées : " + acceptees + ")"),
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex) {
        log.error("Erreur interne non traitée", ex);
        return body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorCode.INTERNAL_ERROR,
                "Erreur interne",
                null));
    }
}
