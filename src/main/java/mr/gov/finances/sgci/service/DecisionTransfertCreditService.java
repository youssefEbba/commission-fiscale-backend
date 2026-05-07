package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionTransfertCredit;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DecisionTransfertCreditRepository;
import mr.gov.finances.sgci.repository.RejetTempResponseRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionTransfertCreditService {

    private final DecisionTransfertCreditRepository decisionRepository;
    private final TransfertCreditRepository transfertRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentTransfertCreditService documentTransfertCreditService;
    private final RejetTempResponseService rejetTempResponseService;
    private final RejetTempResponseRepository rejetTempResponseRepository;

    @Transactional(readOnly = true)
    public List<DecisionCreditDto> findByTransfert(Long transfertCreditId) {
        return decisionRepository.findByTransfertCredit_Id(transfertCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DecisionCreditDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        DecisionTransfertCredit decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision transfert non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long transfertId = decision.getTransfertCredit() != null ? decision.getTransfertCredit().getId() : null;
        if (transfertId != null) {
            boolean stillOpen = !decisionRepository.findByTransfertCredit_IdAndDecisionAndRejetTempStatus(
                    transfertId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!stillOpen) {
                TransfertCredit transfert = transfertRepository.findById(transfertId).orElse(null);
                if (transfert != null && transfert.getStatut() == StatutTransfert.INCOMPLETE) {
                    transfert.setStatut(StatutTransfert.A_RECONTROLER);
                    transfertRepository.save(transfert);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCreditDto saveDecision(Long transfertCreditId,
                                          DecisionCorrectionType decision,
                                          String motifRejet,
                                          Set<TypeDocument> documentsDemandes,
                                          AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role != Role.DGTCP && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP ou le Président peut décider sur une demande de transfert");
        }
        if (decision != DecisionCorrectionType.REJET_TEMP) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seul REJET_TEMP est pris en charge pour le transfert; la validation définitive se fait via POST .../valider");
        }
        if (motifRejet == null || motifRejet.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
        }
        if (documentsDemandes == null || documentsDemandes.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
        }

        TransfertCredit transfert = transfertRepository.findById(transfertCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + transfertCreditId));

        StatutTransfert st = transfert.getStatut();
        if (st == StatutTransfert.TRANSFERE || st == StatutTransfert.REJETE || st == StatutTransfert.ANNULEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision interdite pour le statut: " + st);
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        DecisionTransfertCredit entity = DecisionTransfertCredit.builder()
                .transfertCredit(transfert)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(motifRejet);
        entity.setDocumentsDemandes(new HashSet<>(documentsDemandes));
        entity.setRejetTempStatus(RejetTempStatus.OUVERT);
        entity.setRejetTempResolvedAt(null);
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        transfert.setStatut(StatutTransfert.INCOMPLETE);
        transfertRepository.save(transfert);
        return toDto(entity);
    }

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
            DecisionTransfertCredit decision = decisionRepository.findById(decisionId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision transfert non trouvée: " + decisionId));
            Long transfertId = decision.getTransfertCredit().getId();
            documentTransfertCreditService.upload(transfertId, type, message, file, user);
            return rejetTempResponseRepository.findByDecisionTransfertCredit_IdOrderByCreatedAtAsc(decisionId).stream()
                    .map(this::toResponseDto)
                    .collect(Collectors.toList());
        }
        return rejetTempResponseService.addResponseToTransfertDecision(decisionId, message, user);
    }

    private DecisionCreditDto toDto(DecisionTransfertCredit entity) {
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
                        : List.of())
                .build();
    }

    private RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
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
