package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionCorrectionService {

    private static final Set<StatutDemande> DECISION_ALLOWED_STATUTS = EnumSet.of(
            StatutDemande.RECUE, StatutDemande.INCOMPLETE, StatutDemande.RECEVABLE,
            StatutDemande.EN_EVALUATION, StatutDemande.EN_VALIDATION
    );

    private final DecisionCorrectionRepository decisionRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Transactional
    public DecisionCorrectionDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        DecisionCorrection decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision correction non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long demandeId = decision.getDemandeCorrection() != null ? decision.getDemandeCorrection().getId() : null;
        if (demandeId != null) {
            boolean anyOpen = !decisionRepository.findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(
                    demandeId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!anyOpen) {
                DemandeCorrection demande = demandeRepository.findById(demandeId).orElse(null);
                if (demande != null && demande.getStatut() == StatutDemande.INCOMPLETE) {
                    demande.setStatut(StatutDemande.RECEVABLE);
                    demandeRepository.save(demande);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCorrectionDto saveDecision(Long demandeId, DecisionCorrectionType decision, String motifRejet, Set<TypeDocument> documentsDemandes, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role != Role.DGD && role != Role.DGTCP && role != Role.DGI && role != Role.DGB && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Rôle non autorisé pour la décision: " + role);
        }

        if (role != Role.DGD && role != Role.PRESIDENT) {
            boolean dgdVisa = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                    demandeId, Role.DGD, DecisionCorrectionType.VISA);
            if (!dgdVisa) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le visa DGD est requis en premier");
            }
        }
        if (decision == DecisionCorrectionType.REJET_TEMP) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            if (documentsDemandes == null || documentsDemandes.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
            }
        }

        DemandeCorrection demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeId));

        if (!DECISION_ALLOWED_STATUTS.contains(demande.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision impossible: la demande est en statut " + demande.getStatut()
                            + ". Les décisions ne sont autorisées qu'en statut: " + DECISION_ALLOWED_STATUTS);
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        boolean visaAlreadyForRole = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                demandeId, role, DecisionCorrectionType.VISA);
        if (visaAlreadyForRole) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Décision impossible: un visa a déjà été accordé par ce rôle. Le visa clôture les interactions sur cette demande.");
        }

        if (decision == DecisionCorrectionType.VISA) {
            boolean openRejetForRole = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecisionAndRejetTempStatus(
                    demandeId, role, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT);
            if (openRejetForRole) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "VISA impossible: un ou plusieurs rejets temporaires sont encore ouverts pour ce rôle. "
                                + "Résolvez-les via PUT .../decisions/{id}/resolve pour chaque rejet concerné.");
            }
        }

        DecisionCorrection entity = DecisionCorrection.builder()
                .demandeCorrection(demande)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        entity.setDocumentsDemandes(decision == DecisionCorrectionType.REJET_TEMP
                ? new HashSet<>(documentsDemandes)
                : new HashSet<>());
        entity.setDateDecision(Instant.now());
        entity.setRejetTempStatus(decision == DecisionCorrectionType.REJET_TEMP
                ? RejetTempStatus.OUVERT
                : RejetTempStatus.RESOLU);
        entity.setRejetTempResolvedAt(decision == DecisionCorrectionType.REJET_TEMP ? null : Instant.now());
        entity.setUtilisateur(utilisateur);

        if (decision == DecisionCorrectionType.REJET_TEMP) {
            demande.setStatut(StatutDemande.INCOMPLETE);
        }

        entity = decisionRepository.save(entity);
        demandeRepository.save(demande);
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
                .documentsDemandes(entity.getDocumentsDemandes())
                .dateDecision(entity.getDateDecision())
                .rejetTempStatus(entity.getRejetTempStatus())
                .rejetTempResolvedAt(entity.getRejetTempResolvedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .rejetTempResponses(entity.getRejetTempResponses() != null
                        ? entity.getRejetTempResponses().stream().map(this::toResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
        return mr.gov.finances.sgci.web.dto.RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .documentType(entity.getDocumentType())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
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
