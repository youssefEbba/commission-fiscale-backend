package mr.gov.finances.sgci.notification;

import lombok.Builder;
import lombok.Getter;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.security.AuthenticatedUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class WorkflowNotificationContext {

    private final String entityType;
    private final Long entityId;
    private final String dossierLabel;
    private final AuthenticatedUser actor;
    private final String oldStatus;
    private final String newStatus;
    private final String motif;
    private final Long entrepriseId;
    private final Long autoriteContractanteId;
    private final Long certificatCreditId;
    @Builder.Default
    private final List<Role> roleRecipients = List.of();
    @Builder.Default
    private final List<Long> explicitUserIds = List.of();
    @Builder.Default
    private final Map<String, Object> extraPayload = Map.of();
    private final String messageOverride;

    public Map<String, Object> buildPayload(String eventCode) {
        Map<String, Object> payload = new HashMap<>();
        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }
        payload.put("eventCode", eventCode);
        if (dossierLabel != null) {
            payload.put("dossierLabel", dossierLabel);
        }
        if (oldStatus != null) {
            payload.put("oldStatus", oldStatus);
        }
        if (newStatus != null) {
            payload.put("newStatus", newStatus);
            payload.put("statut", newStatus);
        }
        if (motif != null && !motif.isBlank()) {
            payload.put("motif", motif);
            payload.put("motifRejet", motif);
        }
        if (actor != null) {
            if (actor.getRole() != null) {
                payload.put("acteurRole", actor.getRole().name());
            }
            if (actor.getUserId() != null) {
                payload.put("acteurUserId", actor.getUserId());
            }
        }
        return payload;
    }
}
