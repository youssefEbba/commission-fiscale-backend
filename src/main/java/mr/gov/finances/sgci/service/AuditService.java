package mr.gov.finances.sgci.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AuditLog;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.AuditLogRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Enregistre une action (création, modification, suppression) sur une entité.
     *
     * @param action       CREATE, UPDATE ou DELETE
     * @param entityType   type d'entité (ex: DemandeCorrection, Entreprise)
     * @param entityId     id de l'objet (peut être null pour CREATE avant flush)
     * @param objectSnapshot objet à sérialiser en JSON (null pour DELETE)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void log(AuditAction action, String entityType, String entityId, Object objectSnapshot) {
        String username = "system";
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser) {
            AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
            username = user.getUsername();
            userId = user.getUserId();
        } else if (auth != null && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
        }

        String snapshotJson = null;
        if (objectSnapshot != null) {
            try {
                snapshotJson = objectMapper.writeValueAsString(objectSnapshot);
            } catch (JsonProcessingException e) {
                snapshotJson = "{\"error\":\"serialization\"}";
            }
        } else if (action == AuditAction.DELETE && entityId != null) {
            snapshotJson = "{\"id\":\"" + entityId + "\",\"deleted\":true}";
        }

        AuditLog log = AuditLog.builder()
                .timestamp(Instant.now())
                .userId(userId)
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .objectSnapshot(snapshotJson)
                .build();
        repository.save(log);
    }
}
