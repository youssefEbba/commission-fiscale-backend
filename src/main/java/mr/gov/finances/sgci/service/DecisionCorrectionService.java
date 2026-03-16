package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionCorrectionService {

    private final DecisionCorrectionRepository decisionRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Transactional
    public DecisionCorrectionDto saveDecision(Long demandeId, DecisionCorrectionType decision, String motifRejet, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role != Role.DGD && role != Role.DGTCP && role != Role.DGI && role != Role.DGB && role != Role.PRESIDENT) {
            throw new RuntimeException("Rôle non autorisé pour la décision: " + role);
        }

        if (role != Role.DGD && role != Role.PRESIDENT) {
            boolean dgdVisa = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                    demandeId, Role.DGD, DecisionCorrectionType.VISA);
            if (!dgdVisa) {
                throw new RuntimeException("Le visa DGD est requis en premier");
            }
        }
        if (decision == DecisionCorrectionType.REJET_TEMP && (motifRejet == null || motifRejet.isBlank())) {
            throw new RuntimeException("Le motif de rejet est obligatoire");
        }

        DemandeCorrection demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + demandeId));
        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        DecisionCorrection entity = decisionRepository.findByDemandeCorrectionIdAndRole(demandeId, role)
                .orElse(DecisionCorrection.builder()
                        .demandeCorrection(demande)
                        .role(role)
                        .build());

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        notifyDecision(demande, entity, user);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<DecisionCorrectionDto> findByDemande(Long demandeId) {
        return decisionRepository.findByDemandeCorrectionId(demandeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DecisionCorrectionDto toDto(DecisionCorrection entity) {
        return DecisionCorrectionDto.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .decision(entity.getDecision())
                .motifRejet(entity.getMotifRejet())
                .dateDecision(entity.getDateDecision())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private void notifyDecision(DemandeCorrection demande, DecisionCorrection decision, AuthenticatedUser user) {
        if (demande == null || decision == null) {
            return;
        }
        List<Long> userIds = resolveRelatedUserIds(demande);
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("decision", decision.getDecision().name());
        if (decision.getMotifRejet() != null && !decision.getMotifRejet().isBlank()) {
            payload.put("motifRejet", decision.getMotifRejet());
        }
        if (decision.getRole() != null) {
            payload.put("role", decision.getRole().name());
        }
        if (user != null) {
            payload.put("acteurUserId", user.getUserId());
        }
        String message = "Décision sur demande " + demande.getNumero() + ": " + decision.getDecision();
        notificationService.notifyUsers(userIds, NotificationType.CORRECTION_DECISION,
                "DemandeCorrection", demande.getId(), message, payload);
    }

    private List<Long> resolveRelatedUserIds(DemandeCorrection demande) {
        List<Long> entrepriseUsers = demande.getEntreprise() != null
                ? utilisateurRepository.findByEntrepriseId(demande.getEntreprise().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        List<Long> autoriteUsers = demande.getAutoriteContractante() != null
                ? utilisateurRepository.findByAutoriteContractanteId(demande.getAutoriteContractante().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        return java.util.stream.Stream.concat(entrepriseUsers.stream(), autoriteUsers.stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
