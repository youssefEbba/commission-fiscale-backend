package mr.gov.finances.sgci.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Corps JSON uniforme pour les erreurs API (éviter de parser des messages libres côté front).
 * Le champ {@code error} reprend {@code message} pour compatibilité avec les anciens clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Object details
) {
    public static ErrorResponse of(int httpStatus, String code, String message, Object details) {
        return new ErrorResponse(Instant.now(), httpStatus, code, message, details);
    }

    @JsonProperty("error")
    public String errorAlias() {
        return message;
    }
}
