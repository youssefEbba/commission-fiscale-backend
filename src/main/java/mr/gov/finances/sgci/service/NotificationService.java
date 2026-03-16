package mr.gov.finances.sgci.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Notification;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.repository.NotificationRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.NotificationDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String USER_TOPIC_PREFIX = "/topic/notifications/user/";

    private final NotificationRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public NotificationDto notifyUser(Long utilisateurId,
                                      NotificationType type,
                                      String entityType,
                                      Long entityId,
                                      String message,
                                      Map<String, Object> payload) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        Notification notification = buildNotification(utilisateur, type, entityType, entityId, message, payload);
        notification = repository.save(notification);

        NotificationDto dto = toDto(notification);
        messagingTemplate.convertAndSend(USER_TOPIC_PREFIX + utilisateurId, dto);
        return dto;
    }

    @Transactional
    public List<NotificationDto> notifyUsers(List<Long> utilisateurIds,
                                             NotificationType type,
                                             String entityType,
                                             Long entityId,
                                             String message,
                                             Map<String, Object> payload) {
        if (utilisateurIds == null || utilisateurIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = utilisateurIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        List<Utilisateur> utilisateurs = utilisateurRepository.findAllById(uniqueIds);
        Map<Long, Utilisateur> byId = utilisateurs.stream().collect(Collectors.toMap(Utilisateur::getId, u -> u));

        List<Notification> notifications = uniqueIds.stream()
                .map(byId::get)
                .filter(u -> u != null)
                .map(u -> buildNotification(u, type, entityType, entityId, message, payload))
                .collect(Collectors.toList());
        if (notifications.isEmpty()) {
            return List.of();
        }
        notifications = repository.saveAll(notifications);
        List<NotificationDto> dtos = notifications.stream().map(this::toDto).collect(Collectors.toList());
        notifications.forEach(notification -> {
            NotificationDto dto = toDto(notification);
            messagingTemplate.convertAndSend(USER_TOPIC_PREFIX + notification.getUtilisateur().getId(), dto);
        });
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findByUser(Long utilisateurId) {
        return repository.findByUtilisateurIdOrderByDateCreationDesc(utilisateurId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countUnread(Long utilisateurId) {
        return repository.countByUtilisateurIdAndReadFalse(utilisateurId);
    }

    @Transactional
    public NotificationDto markAsRead(Long id, Long utilisateurId) {
        Notification notification = repository.findByIdAndUtilisateurId(id, utilisateurId)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        notification.setRead(true);
        notification = repository.save(notification);
        return toDto(notification);
    }

    @Transactional
    public void markAllAsRead(Long utilisateurId) {
        List<Notification> notifications = repository.findByUtilisateurIdOrderByDateCreationDesc(utilisateurId);
        notifications.stream().filter(n -> !n.isRead()).forEach(n -> n.setRead(true));
        repository.saveAll(notifications);
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur sérialisation notification", e);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .entityType(notification.getEntityType())
                .entityId(notification.getEntityId())
                .message(notification.getMessage())
                .payload(readPayload(notification.getPayload()))
                .read(notification.isRead())
                .dateCreation(notification.getDateCreation())
                .build();
    }

    private Notification buildNotification(Utilisateur utilisateur,
                                           NotificationType type,
                                           String entityType,
                                           Long entityId,
                                           String message,
                                           Map<String, Object> payload) {
        return Notification.builder()
                .utilisateur(utilisateur)
                .type(type)
                .entityType(entityType)
                .entityId(entityId)
                .message(message)
                .payload(writePayload(payload))
                .read(false)
                .dateCreation(Instant.now())
                .build();
    }
}
