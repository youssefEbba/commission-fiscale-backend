package mr.gov.finances.sgci.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    /**
     * La référence lisible de chaque type d'entité journalisé, en expression HQL sur l'alias {@code e}.
     *
     * <p>Le journal ne garde que le type et l'identifiant interne : l'écran affichait
     * « DemandeCorrection #49 » là où l'agent connaît « DC-0040-11-2026 » (anomalie A29). La
     * référence est résolue à la lecture, par lot, pour les seuls types qui en ont une.
     */
    private static final Set<String> TYPES_AVEC_REFERENCE = Set.of(
            "DemandeCorrection", "CertificatCredit", "UtilisationCredit", "Avenant", "Convention",
            "Marche", "ReferentielProjet", "DemandeRattachement", "Entreprise", "Utilisateur");

    private final AuditLogRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<AuditLogDto> findWithFilters(String username, String entityType, AuditAction action,
                                              Instant from, Instant to, Pageable pageable) {
        Page<AuditLog> page = repository.findWithFilters(username, entityType, action, from, to, pageable);
        Map<String, Map<Long, String>> references = resoudreReferences(page.getContent());
        return page.map(a -> toDto(a, references));
    }

    private Map<String, Map<Long, String>> resoudreReferences(List<AuditLog> logs) {
        Map<String, List<Long>> idsParType = logs.stream()
                .filter(a -> TYPES_AVEC_REFERENCE.contains(a.getEntityType()))
                .filter(a -> idNumerique(a.getEntityId()) != null)
                .collect(Collectors.groupingBy(AuditLog::getEntityType,
                        Collectors.mapping(a -> idNumerique(a.getEntityId()), Collectors.toList())));
        Map<String, Map<Long, String>> out = new HashMap<>();
        idsParType.forEach((type, ids) -> {
            List<Object[]> rows = chargerReferences(type, ids);
            Map<Long, String> parId = new HashMap<>();
            for (Object[] row : rows) {
                if (row[1] != null) {
                    parId.put(((Number) row[0]).longValue(), row[1].toString());
                }
            }
            out.put(type, parId);
        });
        return out;
    }

    /** Requêtes HQL littérales : le type d'entité n'est jamais interpolé. */
    private List<Object[]> chargerReferences(String type, List<Long> ids) {
        TypedQuery<Object[]> query = switch (type) {
            case "DemandeCorrection" -> entityManager.createQuery(
                    "select e.id, coalesce(e.reference, e.numero) from DemandeCorrection e where e.id in :ids", Object[].class);
            case "CertificatCredit" -> entityManager.createQuery(
                    "select e.id, coalesce(e.reference, e.numero) from CertificatCredit e where e.id in :ids", Object[].class);
            case "UtilisationCredit" -> entityManager.createQuery(
                    "select e.id, e.reference from UtilisationCredit e where e.id in :ids", Object[].class);
            case "Avenant" -> entityManager.createQuery(
                    "select e.id, e.numero from Avenant e where e.id in :ids", Object[].class);
            case "Convention" -> entityManager.createQuery(
                    "select e.id, e.reference from Convention e where e.id in :ids", Object[].class);
            case "Marche" -> entityManager.createQuery(
                    "select e.id, coalesce(e.reference, e.numeroMarche) from Marche e where e.id in :ids", Object[].class);
            case "ReferentielProjet" -> entityManager.createQuery(
                    "select e.id, coalesce(e.numero, e.nomProjet) from ReferentielProjet e where e.id in :ids", Object[].class);
            case "DemandeRattachement" -> entityManager.createQuery(
                    "select e.id, e.reference from DemandeRattachement e where e.id in :ids", Object[].class);
            case "Entreprise" -> entityManager.createQuery(
                    "select e.id, e.raisonSociale from Entreprise e where e.id in :ids", Object[].class);
            case "Utilisateur" -> entityManager.createQuery(
                    "select e.id, e.username from Utilisateur e where e.id in :ids", Object[].class);
            default -> null;
        };
        if (query == null) {
            return List.of();
        }
        return query.setParameter("ids", ids.stream().distinct().toList()).getResultList();
    }

    private AuditLogDto toDto(AuditLog a, Map<String, Map<Long, String>> references) {
        Long id = idNumerique(a.getEntityId());
        String reference = id == null ? null
                : references.getOrDefault(a.getEntityType(), Map.of()).get(id);
        return AuditLogDto.builder()
                .id(a.getId())
                .timestamp(a.getTimestamp())
                .userId(a.getUserId())
                .username(a.getUsername())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .entityReference(reference)
                .objectSnapshot(a.getObjectSnapshot())
                .motif(a.getMotif() != null ? a.getMotif() : motifDuSnapshot(a.getObjectSnapshot()))
                .build();
    }

    /**
     * Les substitutions antérieures au 02/11/2026 rangeaient leur motif dans le snapshot et non dans
     * la colonne dédiée : on l'y reprend, pour que l'écran ne les montre pas sans motif.
     */
    private String motifDuSnapshot(String snapshot) {
        if (snapshot == null || !snapshot.contains("\"motif\"")) {
            return null;
        }
        try {
            JsonNode motif = objectMapper.readTree(snapshot).get("motif");
            return motif != null && motif.isTextual() && !motif.asText().isBlank() ? motif.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Long idNumerique(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(entityId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
