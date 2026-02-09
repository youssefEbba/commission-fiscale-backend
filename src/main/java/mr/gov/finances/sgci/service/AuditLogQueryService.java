package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AuditLog;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.AuditLogRepository;
import mr.gov.finances.sgci.web.dto.AuditLogDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository repository;

    @Transactional(readOnly = true)
    public Page<AuditLogDto> findWithFilters(String username, String entityType, AuditAction action,
                                              Instant from, Instant to, Pageable pageable) {
        return repository.findWithFilters(username, entityType, action, from, to, pageable)
                .map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog a) {
        return AuditLogDto.builder()
                .id(a.getId())
                .timestamp(a.getTimestamp())
                .userId(a.getUserId())
                .username(a.getUsername())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .objectSnapshot(a.getObjectSnapshot())
                .build();
    }
}
