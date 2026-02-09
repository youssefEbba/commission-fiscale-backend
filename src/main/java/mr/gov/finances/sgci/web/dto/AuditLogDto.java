package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AuditAction;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {

    private Long id;
    private Instant timestamp;
    private Long userId;
    private String username;
    private AuditAction action;
    private String entityType;
    private String entityId;
    private String objectSnapshot;
}
