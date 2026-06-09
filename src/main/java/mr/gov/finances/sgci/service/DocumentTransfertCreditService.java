package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentTransfertCredit;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.repository.DecisionTransfertCreditRepository;
import mr.gov.finances.sgci.repository.DocumentTransfertCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentTransfertCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTransfertCreditService {

    private final DocumentTransfertCreditRepository repository;
    private final TransfertCreditRepository transfertRepository;
    private final DecisionTransfertCreditRepository decisionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;
    private final RejetTempResponseService rejetTempResponseService;
    private final WorkflowNotificationHelper workflowNotificationHelper;

    private static final EnumSet<StatutTransfert> STATUTS_DEPOT =
            EnumSet.of(StatutTransfert.DEMANDE, StatutTransfert.EN_COURS, StatutTransfert.VALIDE,
                    StatutTransfert.INCOMPLETE, StatutTransfert.A_RECONTROLER);

    @Transactional
    public DocumentTransfertCreditDto upload(Long transfertCreditId, String codeDocument, MultipartFile file) throws IOException {
        return upload(transfertCreditId, codeDocument, null, file, null, null);
    }

    @Transactional
    public DocumentTransfertCreditDto upload(Long transfertCreditId, String codeDocument, String message, MultipartFile file,
                                             AuthenticatedUser user) throws IOException {
        return upload(transfertCreditId, codeDocument, message, file, user, null);
    }

    /**
     * @param restrictRejetTempResponseToDecisionId si non nul (réponse à un rejet ciblée), le message est obligatoire
     *                                              et {@link RejetTempResponseService#recordTransfertUploadResponse}
     *                                              n’associe la pièce qu’à cette décision.
     */
    @Transactional
    public DocumentTransfertCreditDto upload(Long transfertCreditId, String codeDocument, String message, MultipartFile file,
                                             AuthenticatedUser user, Long restrictRejetTempResponseToDecisionId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        requirementValidator.validateUpload(ProcessusDocument.TRANSFERT_CREDIT, codeDocument, file);

        TransfertCredit transfert = transfertRepository.findById(transfertCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + transfertCreditId));

        StatutTransfert st = transfert.getStatut();
        if (!STATUTS_DEPOT.contains(st)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Dépôt de pièces interdit pour le statut: " + st);
        }

        boolean askedByOpenRejetTemp;
        if (restrictRejetTempResponseToDecisionId != null) {
            askedByOpenRejetTemp = decisionRepository.findById(restrictRejetTempResponseToDecisionId)
                    .filter(d -> d.getTransfertCredit() != null && d.getTransfertCredit().getId().equals(transfert.getId()))
                    .filter(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP && d.getRejetTempStatus() == RejetTempStatus.OUVERT)
                    .map(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument))
                    .orElse(false);
        } else {
            askedByOpenRejetTemp = decisionRepository.findByTransfertCredit_IdAndDecisionAndRejetTempStatus(
                            transfert.getId(),
                            DecisionCorrectionType.REJET_TEMP,
                            RejetTempStatus.OUVERT
                    ).stream().anyMatch(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument));
        }

        if (askedByOpenRejetTemp && (message == null || message.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }
        if (askedByOpenRejetTemp && (user == null || user.getUserId() == null)) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise pour joindre une pièce à un rejet temporaire");
        }

        int nextVersion = 1;
        DocumentTransfertCredit previous = repository.findByTransfertCreditIdAndCodeDocumentAndActifTrue(transfertCreditId, codeDocument)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentTransfertCredit doc = DocumentTransfertCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .transfertCredit(transfert)
                .build();

        doc = repository.save(doc);
        if (transfert.getStatut() == StatutTransfert.DEMANDE) {
            transfert.setStatut(StatutTransfert.EN_COURS);
            transfertRepository.save(transfert);
            workflowNotificationHelper.transfert(transfert, WorkflowEventCode.TRANSFERT_EN_COURS, user);
        }
        DocumentTransfertCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentTransfertCredit", String.valueOf(doc.getId()), result);

        if (askedByOpenRejetTemp) {
            rejetTempResponseService.recordTransfertUploadResponse(transfert.getId(), codeDocument, message, doc, user,
                    restrictRejetTempResponseToDecisionId);
        }

        return result;
    }

    /**
     * Lors d'une nouvelle demande après rejet, les anciennes pièces actives ne doivent pas valider DGTCP.
     */
    @Transactional
    public void deactivateAllForTransfert(Long transfertCreditId) {
        repository.findByTransfertCreditId(transfertCreditId).stream()
                .filter(d -> Boolean.TRUE.equals(d.getActif()))
                .forEach(d -> {
                    d.setActif(false);
                    repository.save(d);
                });
    }

    @Transactional(readOnly = true)
    public List<DocumentTransfertCreditDto> findByTransfertCreditId(Long transfertCreditId) {
        return repository.findByTransfertCreditId(transfertCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> findActiveDocumentTypes(Long transfertCreditId) {
        return repository.findByTransfertCreditIdAndActifTrue(transfertCreditId)
                .stream()
                .map(DocumentTransfertCredit::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentTransfertCreditDto toDto(DocumentTransfertCredit d) {
        return DocumentTransfertCreditDto.builder()
                .id(d.getId())
                .codeDocument(d.getCodeDocument())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }
}
