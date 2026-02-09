package mr.gov.finances.sgci.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.AuditLog;
import mr.gov.finances.sgci.domain.enums.AuditAction;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:username IS NULL OR a.username = :username) AND " +
            "(:entityType IS NULL OR a.entityType = :entityType) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:from IS NULL OR a.timestamp >= :from) AND " +
            "(:to IS NULL OR a.timestamp <= :to) " +
            "ORDER BY a.timestamp DESC")
    Page<AuditLog> findWithFilters(
            @Param("username") String username,
            @Param("entityType") String entityType,
            @Param("action") AuditAction action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
