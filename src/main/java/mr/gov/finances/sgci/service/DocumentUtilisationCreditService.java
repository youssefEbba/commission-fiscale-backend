package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.DecisionUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentUtilisationCreditService {

    private final DocumentUtilisationCreditRepository repository;
    private final UtilisationCreditRepository utilisationRepository;
    private final DecisionUtilisationCreditRepository decisionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;
    private final RejetTempResponseService rejetTempResponseService;

    @Transactional
    public DocumentUtilisationCreditDto upload(Long utilisationCreditId, TypeDocument type, String message, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        UtilisationCredit utilisation = utilisationRepository.findById(utilisationCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + utilisationCreditId));

        ProcessusDocument processus = resolveProcessus(utilisation);
        requirementValidator.validateUpload(processus, type, file);

        int nextVersion = 1;
        DocumentUtilisationCredit previous = repository.findByUtilisationCreditIdAndTypeAndActifTrue(utilisationCreditId, type)
                .orElse(null);
        if (previous != null) {
            assertReplacementAllowed(utilisation, type, user);
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        boolean askedByOpenRejetTemp = decisionRepository.findByUtilisationCreditIdAndDecisionAndRejetTempStatus(
                        utilisation.getId(),
                        DecisionCorrectionType.REJET_TEMP,
                        RejetTempStatus.OUVERT
                ).stream().anyMatch(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(type));

        if (askedByOpenRejetTemp && (message == null || message.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentUtilisationCredit doc = DocumentUtilisationCredit.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .utilisationCredit(utilisation)
                .build();
        doc = repository.save(doc);
        DocumentUtilisationCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentUtilisationCredit", String.valueOf(doc.getId()), result);

        if (askedByOpenRejetTemp) {
            rejetTempResponseService.recordUtilisationUploadResponse(utilisation.getId(), type, message, doc, user);
        }

        return result;
    }

    private void assertReplacementAllowed(UtilisationCredit utilisation, TypeDocument type, AuthenticatedUser user) {
        if (utilisation == null || utilisation.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Utilisation invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Remplacement interdit: réservé à l'Entreprise");
        }
        if (utilisation.getStatut() != StatutUtilisation.INCOMPLETE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: l'utilisation n'est pas en statut INCOMPLETE");
        }
        boolean asked = decisionRepository.findByUtilisationCreditId(utilisation.getId()).stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT
                        && d.getDocumentsDemandes() != null
                        && d.getDocumentsDemandes().contains(type));
        if (!asked) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: aucun acteur n'a demandé ce document");
        }
    }

    private ProcessusDocument resolveProcessus(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (utilisation instanceof UtilisationTVAInterieure t && t.getTypeAchat() != null) {
                return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
            }
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return ProcessusDocument.UTILISATION_CI;
    }

    @Transactional(readOnly = true)
    public List<DocumentUtilisationCreditDto> findByUtilisationCreditId(Long utilisationCreditId) {
        return repository.findByUtilisationCreditId(utilisationCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TypeDocument> findActiveDocumentTypes(Long utilisationCreditId) {
        return repository.findByUtilisationCreditIdAndActifTrue(utilisationCreditId)
                .stream()
                .map(DocumentUtilisationCredit::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentUtilisationCreditDto toDto(DocumentUtilisationCredit d) {
        return DocumentUtilisationCreditDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }
}
