package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.service.AuditLogQueryService;
import mr.gov.finances.sgci.web.dto.AuditLogDto;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogQueryService queryService;

    /**
     * Liste paginée des transactions (qui, quand, quoi, objet).
     * Filtres optionnels : username, entityType, action, dateFrom, dateTo.
     */
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AuditLogDto>> getAll(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(queryService.findWithFilters(username, entityType, action, dateFrom, dateTo, pageable));
    }
}
