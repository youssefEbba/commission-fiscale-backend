package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private Long id;
    private NotificationType type;
    private String entityType;
    private Long entityId;
    private String message;
    private Map<String, Object> payload;
    private boolean read;
    private Instant dateCreation;
}
