package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.repository.*;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RejetTempResponseService {

    private final RejetTempResponseRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final DecisionCertificatCreditRepository decisionCertificatCreditRepository;
    private final DecisionUtilisationCreditRepository decisionUtilisationCreditRepository;
    private final DecisionCorrectionRepository decisionCorrectionRepository;
    private final DecisionTransfertCreditRepository decisionTransfertCreditRepository;
    private final WorkflowNotificationHelper workflowNotificationHelper;

    @Transactional
    public List<RejetTempResponseDto> addResponseToCertificatDecision(Long decisionId, String message, AuthenticatedUser user) {
        DecisionCertificatCredit decision = decisionCertificatCreditRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision certificat non trouvée: " + decisionId));
        assertDecisionOpenRejetTemp(decision.getDecision(), decision.getRejetTempStatus());
        Utilisateur utilisateur = resolveUtilisateur(user);

        RejetTempResponse entity = RejetTempResponse.builder()
                .message(validateMessage(message))
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCertificatCredit(decision)
                .build();
        entity = repository.save(entity);
        notifyCertificatReponse(decision, user, null);
        return List.of(toDto(entity));
    }

    @Transactional
    public List<RejetTempResponseDto> addResponseToUtilisationDecision(Long decisionId, String message, AuthenticatedUser user) {
        DecisionUtilisationCredit decision = decisionUtilisationCreditRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision utilisation non trouvée: " + decisionId));
        assertDecisionOpenRejetTemp(decision.getDecision(), decision.getRejetTempStatus());
        Utilisateur utilisateur = resolveUtilisateur(user);

        RejetTempResponse entity = RejetTempResponse.builder()
                .message(validateMessage(message))
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionUtilisationCredit(decision)
                .build();
        entity = repository.save(entity);
        notifyUtilisationReponse(decision, user, null);
        return List.of(toDto(entity));
    }

    @Transactional
    public List<RejetTempResponseDto> addResponseToCorrectionDecision(Long decisionId, String message, AuthenticatedUser user) {
        DecisionCorrection decision = decisionCorrectionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision correction non trouvée: " + decisionId));
        assertDecisionOpenRejetTemp(decision.getDecision(), decision.getRejetTempStatus());
        Utilisateur utilisateur = resolveUtilisateur(user);

        RejetTempResponse entity = RejetTempResponse.builder()
                .message(validateMessage(message))
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCorrection(decision)
                .build();
        entity = repository.save(entity);
        notifyCorrectionReponse(decision, user, null);
        return List.of(toDto(entity));
    }

    @Transactional
    public List<RejetTempResponseDto> addResponseToTransfertDecision(Long decisionId, String message, AuthenticatedUser user) {
        DecisionTransfertCredit decision = decisionTransfertCreditRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision transfert non trouvée: " + decisionId));
        assertDecisionOpenRejetTemp(decision.getDecision(), decision.getRejetTempStatus());
        Utilisateur utilisateur = resolveUtilisateur(user);

        RejetTempResponse entity = RejetTempResponse.builder()
                .message(validateMessage(message))
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionTransfertCredit(decision)
                .build();
        entity = repository.save(entity);
        notifyTransfertReponse(decision, user);
        return List.of(toDto(entity));
    }

    @Transactional
    public void recordCertificatUploadResponse(Long certificatId, String codeDocument, String message,
                                              DocumentCertificatCredit doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionCertificatCredit> decisions = decisionCertificatCreditRepository
                .findByCertificatCreditIdAndDecisionAndRejetTempStatus(certificatId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .codeDocument(codeDocument)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCertificatCredit(decision)
                .build()));
        decisions.stream().findFirst().ifPresent(d -> notifyCertificatReponse(d, user, codeDocument));
    }

    @Transactional
    public void recordUtilisationUploadResponse(Long utilisationId, String codeDocument, String message,
                                               DocumentUtilisationCredit doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionUtilisationCredit> decisions = decisionUtilisationCreditRepository
                .findByUtilisationCreditIdAndDecisionAndRejetTempStatus(utilisationId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .codeDocument(codeDocument)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionUtilisationCredit(decision)
                .build()));
        decisions.stream().findFirst().ifPresent(d -> notifyUtilisationReponse(d, user, codeDocument));
    }

    /**
     * Enregistre une réponse « rejet temporaire » liée au dépôt d’un document transfert (GED).
     *
     * @param onlyDecisionId si non nul, n’alimente que cette décision (réponse ciblée à un {@code decisionId} précis) ;
     *                       sinon, toute décision REJET_TEMP ouverte du transfert qui demande ce {@code type} reçoit une ligne.
     */
    @Transactional
    public void recordTransfertUploadResponse(Long transfertCreditId, String codeDocument, String message,
                                              DocumentTransfertCredit doc, AuthenticatedUser user, Long onlyDecisionId) {
        if (doc == null) {
            return;
        }
        List<DecisionTransfertCredit> decisions;
        if (onlyDecisionId != null) {
            DecisionTransfertCredit one = decisionTransfertCreditRepository.findById(onlyDecisionId).orElse(null);
            if (one == null || one.getTransfertCredit() == null
                    || !one.getTransfertCredit().getId().equals(transfertCreditId)) {
                return;
            }
            if (one.getDecision() != DecisionCorrectionType.REJET_TEMP || one.getRejetTempStatus() != RejetTempStatus.OUVERT) {
                return;
            }
            if (one.getDocumentsDemandes() == null || !one.getDocumentsDemandes().contains(codeDocument)) {
                return;
            }
            decisions = List.of(one);
        } else {
            decisions = decisionTransfertCreditRepository
                    .findByTransfertCredit_IdAndDecisionAndRejetTempStatus(transfertCreditId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                    .stream()
                    .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument))
                    .collect(Collectors.toList());
        }
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .codeDocument(codeDocument)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionTransfertCredit(decision)
                .build()));
        decisions.stream().findFirst().ifPresent(d -> notifyTransfertReponse(d, user));
    }

    /** Vérifie qu’une décision transfert est bien un {@code REJET_TEMP} ouvert (réponse entreprise autorisée). */
    public void assertTransfertDecisionOpenRejetTemp(DecisionTransfertCredit decision) {
        if (decision == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision transfert invalide");
        }
        assertDecisionOpenRejetTemp(decision.getDecision(), decision.getRejetTempStatus());
    }

    @Transactional
    public void recordCorrectionUploadResponse(Long demandeId, String codeDocument, String message,
                                              Document doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionCorrection> decisions = decisionCorrectionRepository
                .findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(demandeId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .codeDocument(codeDocument)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCorrection(decision)
                .build()));
        decisions.stream().findFirst().ifPresent(d -> notifyCorrectionReponse(d, user, codeDocument));
    }

    private void notifyCertificatReponse(DecisionCertificatCredit decision, AuthenticatedUser user, String codeDocument) {
        if (decision == null || decision.getCertificatCredit() == null) {
            return;
        }
        workflowNotificationHelper.certificatRejetTempReponse(decision.getCertificatCredit(), user, decision.getRole(),
                codeDocument, decision.getId());
    }

    private void notifyUtilisationReponse(DecisionUtilisationCredit decision, AuthenticatedUser user, String codeDocument) {
        if (decision == null || decision.getUtilisationCredit() == null) {
            return;
        }
        workflowNotificationHelper.utilisationRejetTempReponse(decision.getUtilisationCredit(), user, decision.getRole(),
                decision.getId());
    }

    private void notifyCorrectionReponse(DecisionCorrection decision, AuthenticatedUser user, String codeDocument) {
        if (decision == null || decision.getDemandeCorrection() == null) {
            return;
        }
        workflowNotificationHelper.correctionRejetTempReponse(decision.getDemandeCorrection(), user, decision.getRole(),
                codeDocument, decision.getId());
    }

    private void notifyTransfertReponse(DecisionTransfertCredit decision, AuthenticatedUser user) {
        if (decision == null || decision.getTransfertCredit() == null) {
            return;
        }
        workflowNotificationHelper.transfert(decision.getTransfertCredit(), WorkflowEventCode.TRANSFERT_REJET_TEMP_REPONSE,
                user, decision.getId(), decision.getDocumentsDemandes());
    }

    private void assertDecisionOpenRejetTemp(DecisionCorrectionType decision, RejetTempStatus status) {
        if (decision != DecisionCorrectionType.REJET_TEMP || status != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Réponse rejet temporaire interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
    }

    private Utilisateur resolveUtilisateur(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        return utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
    }

    private String validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }
        return message;
    }

    private RejetTempResponseDto toDto(RejetTempResponse entity) {
        return RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .codeDocument(entity.getCodeDocument())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }
}
