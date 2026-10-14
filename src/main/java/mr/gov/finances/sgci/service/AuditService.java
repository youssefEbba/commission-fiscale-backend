package mr.gov.finances.sgci.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.AuditLog;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.AuditLogRepository;
import mr.gov.finances.sgci.security.ActeurCourant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    /** Limite pratique pour LONGTEXT (évite snapshots aberrants). */
    private static final int MAX_SNAPSHOT_CHARS = 1_000_000;

    private final AuditLogRepository repository;
    /**
     * Mapper de l'application, injecté : il embarque le module JavaTime. Un {@code new
     * ObjectMapper()} ne le fait pas et échouait sur tout DTO portant un {@code Instant}.
     */
    private final ObjectMapper objectMapper;

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
        log(action, entityType, entityId, objectSnapshot, null);
    }

    /**
     * Variante avec motif obligatoire pour {@link AuditAction#ADMIN_CORRECTION} — corrections manuelles
     * d'un administrateur système sur un document ou une information, en dehors du workflow normal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void log(AuditAction action, String entityType, String entityId, Object objectSnapshot, String motif) {
        // Partagé avec l'historisation des statuts : deux traces du même geste doivent nommer le
        // même acteur, sinon les rapprocher devient un exercice de divination.
        ActeurCourant.Acteur acteur = ActeurCourant.lire();
        String username = acteur.username();
        Long userId = acteur.userId();

        String snapshotJson = null;
        if (objectSnapshot != null) {
            try {
                snapshotJson = objectMapper.writeValueAsString(objectSnapshot);
            } catch (JsonProcessingException e) {
                // Journalisé : un instantané perdu passait jusqu'ici totalement inaperçu.
                log.warn("Audit {} {}#{} : instantané non sérialisable ({})",
                        action, entityType, entityId, e.getOriginalMessage());
                snapshotJson = "{\"error\":\"serialization\"}";
            }
        } else if (action == AuditAction.DELETE && entityId != null) {
            snapshotJson = "{\"id\":\"" + entityId + "\",\"deleted\":true}";
        }
        snapshotJson = truncateSnapshot(snapshotJson);

        try {
            AuditLog entry = AuditLog.builder()
                    .timestamp(Instant.now())
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .objectSnapshot(snapshotJson)
                    .motif(motif)
                    .build();
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Échec enregistrement audit {} {} {}: {}", action, entityType, entityId, e.getMessage());
        }
    }

    private static String truncateSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.length() <= MAX_SNAPSHOT_CHARS) {
            return snapshotJson;
        }
        return snapshotJson.substring(0, MAX_SNAPSHOT_CHARS);
    }
}
