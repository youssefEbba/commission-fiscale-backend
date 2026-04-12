package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DecisionUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.RejetTempResponseRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionUtilisationCreditService {

    private final DecisionUtilisationCreditRepository decisionRepository;
    private final UtilisationCreditRepository utilisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentUtilisationCreditService documentUtilisationCreditService;
    private final RejetTempResponseService rejetTempResponseService;
    private final RejetTempResponseRepository rejetTempResponseRepository;

    @Transactional
    public DecisionCreditDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        DecisionUtilisationCredit decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision utilisation non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long utilisationId = decision.getUtilisationCredit() != null ? decision.getUtilisationCredit().getId() : null;
        if (utilisationId != null) {
            boolean anyOpen = !decisionRepository.findByUtilisationCreditIdAndDecisionAndRejetTempStatus(
                    utilisationId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!anyOpen) {
                UtilisationCredit utilisation = utilisationRepository.findById(utilisationId)
                        .orElse(null);
                if (utilisation != null && utilisation.getStatut() == StatutUtilisation.INCOMPLETE) {
                    utilisation.setStatut(StatutUtilisation.A_RECONTROLER);
                    utilisationRepository.save(utilisation);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCreditDto saveDecision(Long utilisationCreditId,
                                         DecisionCorrectionType decision,
                                         String motifRejet,
                                         Set<TypeDocument> documentsDemandes,
                                         AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role != Role.DGD && role != Role.DGTCP && role != Role.DGI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Rôle non autorisé pour la décision utilisation: " + role);
        }

        if (decision == DecisionCorrectionType.REJET_TEMP) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            if (documentsDemandes == null || documentsDemandes.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
            }
        }

        UtilisationCredit utilisation = utilisationRepository.findById(utilisationCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + utilisationCreditId));

        Set<StatutUtilisation> decisionInterdits = EnumSet.of(
                StatutUtilisation.LIQUIDEE,
                StatutUtilisation.APUREE,
                StatutUtilisation.REJETEE);
        if (decisionInterdits.contains(utilisation.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision interdite: l'utilisation est en statut "
                            + utilisation.getStatut() + " (liquidée, apurée ou rejetée définitivement)");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        boolean visaAlreadyForRole = decisionRepository.existsByUtilisationCreditIdAndRoleAndDecision(
                utilisationCreditId, role, DecisionCorrectionType.VISA);
        if (visaAlreadyForRole) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Décision impossible: un visa a déjà été accordé par ce rôle. Le visa clôture les interactions sur cette demande.");
        }

        if (decision == DecisionCorrectionType.VISA) {
            boolean openRejetForRole = decisionRepository.existsByUtilisationCreditIdAndRoleAndDecisionAndRejetTempStatus(
                    utilisationCreditId, role, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT);
            if (openRejetForRole) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "VISA impossible: un ou plusieurs rejets temporaires sont encore ouverts pour ce rôle. "
                                + "Résolvez-les via PUT .../decisions/{id}/resolve pour chaque rejet concerné.");
            }
        }

        DecisionUtilisationCredit entity = DecisionUtilisationCredit.builder()
                .utilisationCredit(utilisation)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        if (decision == DecisionCorrectionType.REJET_TEMP) {
            entity.setDocumentsDemandes(new HashSet<>(documentsDemandes));
            entity.setRejetTempStatus(RejetTempStatus.OUVERT);
            entity.setRejetTempResolvedAt(null);
            utilisation.setStatut(StatutUtilisation.INCOMPLETE);
        } else {
            entity.getDocumentsDemandes().clear();
            entity.setRejetTempStatus(RejetTempStatus.RESOLU);
            entity.setRejetTempResolvedAt(Instant.now());
        }
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        utilisationRepository.save(utilisation);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<DecisionCreditDto> findByUtilisation(Long utilisationCreditId) {
        return decisionRepository.findByUtilisationCreditId(utilisationCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Réponse à un rejet temporaire : JSON (message seul) ou multipart (message + fichier optionnel).
     * Avec fichier, {@code type} est obligatoire (même contrat que POST .../documents?type=).
     */
    @Transactional
    public List<RejetTempResponseDto> respondRejetTemp(
            Long decisionId,
            String message,
            MultipartFile file,
            TypeDocument type,
            AuthenticatedUser user) throws IOException {
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }
        if (file != null && !file.isEmpty()) {
            if (type == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le type de document est obligatoire lors de l'envoi d'un fichier");
            }
            DecisionUtilisationCredit decision = decisionRepository.findById(decisionId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision utilisation non trouvée: " + decisionId));
            Long utilId = decision.getUtilisationCredit().getId();
            documentUtilisationCreditService.upload(utilId, type, message, file, user);
            return rejetTempResponseRepository.findByDecisionUtilisationCredit_IdOrderByCreatedAtAsc(decisionId).stream()
                    .map(this::toResponseDto)
                    .collect(Collectors.toList());
        }
        return rejetTempResponseService.addResponseToUtilisationDecision(decisionId, message, user);
    }

    private DecisionCreditDto toDto(DecisionUtilisationCredit entity) {
        return DecisionCreditDto.builder()
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
}
