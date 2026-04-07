package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
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
        return List.of(toDto(entity));
    }

    @Transactional
    public void recordCertificatUploadResponse(Long certificatId, mr.gov.finances.sgci.domain.enums.TypeDocument type, String message,
                                              DocumentCertificatCredit doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionCertificatCredit> decisions = decisionCertificatCreditRepository
                .findByCertificatCreditIdAndDecisionAndRejetTempStatus(certificatId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(type))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .documentType(type)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCertificatCredit(decision)
                .build()));
    }

    @Transactional
    public void recordUtilisationUploadResponse(Long utilisationId, mr.gov.finances.sgci.domain.enums.TypeDocument type, String message,
                                               DocumentUtilisationCredit doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionUtilisationCredit> decisions = decisionUtilisationCreditRepository
                .findByUtilisationCreditIdAndDecisionAndRejetTempStatus(utilisationId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(type))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .documentType(type)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionUtilisationCredit(decision)
                .build()));
    }

    @Transactional
    public void recordCorrectionUploadResponse(Long demandeId, mr.gov.finances.sgci.domain.enums.TypeDocument type, String message,
                                              Document doc, AuthenticatedUser user) {
        if (doc == null) {
            return;
        }
        List<DecisionCorrection> decisions = decisionCorrectionRepository
                .findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(demandeId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)
                .stream()
                .filter(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(type))
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = resolveUtilisateur(user);
        String validated = validateMessage(message);
        decisions.forEach(decision -> repository.save(RejetTempResponse.builder()
                .message(validated)
                .documentUrl(doc.getChemin())
                .documentType(type)
                .documentVersion(doc.getVersion())
                .createdAt(Instant.now())
                .utilisateur(utilisateur)
                .decisionCorrection(decision)
                .build()));
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
                .documentType(entity.getDocumentType())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }
}
