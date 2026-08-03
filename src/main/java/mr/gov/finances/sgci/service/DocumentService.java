package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final DecisionCorrectionRepository decisionCorrectionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;
    private final RejetTempResponseService rejetTempResponseService;

    @Transactional
    public DocumentDto upload(Long demandeCorrectionId, String codeDocument, String message, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        DemandeCorrection demande = demandeRepository.findById(demandeCorrectionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeCorrectionId));

        assertRoleAllowedToUpload(user, codeDocument, demande);
        requirementValidator.validateUpload(ProcessusDocument.CORRECTION_OFFRE_FISCALE, codeDocument, file);

        int nextVersion = 1;
        Document previous = documentRepository.findByDemandeCorrectionIdAndCodeDocumentAndActifTrue(demandeCorrectionId, codeDocument)
                .orElse(null);
        if (previous != null) {
            if (!isPresidentLettreAdoptionReplacement(demande, codeDocument, user)) {
                assertReplacementAllowed(demande, codeDocument, user);
            }
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        } else {
            nextVersion = documentRepository
                    .findTopByDemandeCorrection_IdAndCodeDocumentOrderByVersionDesc(demandeCorrectionId, codeDocument)
                    .map(d -> d.getVersion() != null ? d.getVersion() + 1 : 1)
                    .orElse(1);
        }

        boolean askedByOpenRejetTemp = decisionCorrectionRepository.findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(
                        demande.getId(),
                        DecisionCorrectionType.REJET_TEMP,
                        RejetTempStatus.OUVERT
                ).stream().anyMatch(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument));

        if (askedByOpenRejetTemp && (message == null || message.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        Document doc = Document.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .demandeCorrection(demande)
                .build();
        doc = documentRepository.save(doc);
        DocumentDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "Document", String.valueOf(doc.getId()), result);

        if (askedByOpenRejetTemp) {
            rejetTempResponseService.recordCorrectionUploadResponse(demande.getId(), codeDocument, message, doc, user);
        }

        return result;
    }

    /**
     * Remplacement d'un document par un administrateur (ADMIN_SI), à tout moment quel que soit le
     * statut de la demande (ex: le document initialement uploadé ne correspond pas à ce qui était
     * demandé, constaté après le visa). Motif obligatoire, journalisé dans l'audit sous
     * {@link AuditAction#ADMIN_CORRECTION}. L'ancienne version est désactivée (historique conservé),
     * pas supprimée.
     */
    @Transactional
    public DocumentDto adminReplace(Long demandeCorrectionId, String codeDocument, String motif, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Correction administrateur réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de la correction administrateur est obligatoire");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        DemandeCorrection demande = demandeRepository.findById(demandeCorrectionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeCorrectionId));

        Document previous = documentRepository.findByDemandeCorrectionIdAndCodeDocumentAndActifTrue(demandeCorrectionId, codeDocument)
                .orElse(null);
        int nextVersion = 1;
        if (previous != null) {
            previous.setActif(false);
            documentRepository.save(previous);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        } else {
            nextVersion = documentRepository
                    .findTopByDemandeCorrection_IdAndCodeDocumentOrderByVersionDesc(demandeCorrectionId, codeDocument)
                    .map(d -> d.getVersion() != null ? d.getVersion() + 1 : 1)
                    .orElse(1);
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        Document doc = Document.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .demandeCorrection(demande)
                .build();
        doc = documentRepository.save(doc);
        DocumentDto result = toDto(doc);
        auditService.log(AuditAction.ADMIN_CORRECTION, "Document", String.valueOf(doc.getId()), result, motif);
        return result;
    }

    private void assertReplacementAllowed(DemandeCorrection demande, String codeDocument, AuthenticatedUser user) {
        if (demande == null || demande.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Demande de correction invalide");
        }

        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Remplacement interdit: réservé à l'Autorité Contractante");
        }

        if (demande.getStatut() != StatutDemande.INCOMPLETE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: la demande n'est pas en statut INCOMPLETE");
        }

        boolean asked = decisionCorrectionRepository.findByDemandeCorrectionId(demande.getId()).stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT
                        && d.getDocumentsDemandes() != null
                        && d.getDocumentsDemandes().contains(codeDocument));

        if (!asked) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: aucun rejet temporaire ouvert ne demande ce document");
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> findByDemandeCorrectionId(Long demandeCorrectionId) {
        return documentRepository.findByDemandeCorrectionId(demandeCorrectionId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> findActiveDocumentTypes(Long demandeCorrectionId) {
        return documentRepository.findByDemandeCorrectionIdAndActifTrue(demandeCorrectionId)
                .stream()
                .map(Document::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void assertActiveDocumentPresent(Long demandeCorrectionId, String codeDocument) {
        assertActiveDocumentPresent(demandeCorrectionId, codeDocument, "avant visa");
    }

    @Transactional(readOnly = true)
    public void assertActiveDocumentPresent(Long demandeCorrectionId, String codeDocument, String context) {
        if (demandeCorrectionId == null || codeDocument == null || codeDocument.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Document requis manquant");
        }
        boolean present = documentRepository
                .findByDemandeCorrectionIdAndCodeDocumentAndActifTrue(demandeCorrectionId, codeDocument)
                .isPresent();
        if (!present) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Document actif requis " + context + ": " + codeDocument);
        }
    }

    private void assertRoleAllowedToUpload(AuthenticatedUser user, String codeDocument, DemandeCorrection demande) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() == Role.DGI && !"CREDIT_INTERIEUR".equals(codeDocument)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Le DGI ne peut téléverser que le document CREDIT_INTERIEUR");
        }
        if (user.getRole() == Role.PRESIDENT) {
            if (!"LETTRE_ADOPTION".equals(codeDocument)) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Le Président ne peut téléverser que le document LETTRE_ADOPTION");
            }
            if (demande == null || demande.getStatut() != StatutDemande.EN_VALIDATION) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "La lettre d'adoption ne peut être déposée qu'en statut EN_VALIDATION");
            }
        }
    }

    private static boolean isPresidentLettreAdoptionReplacement(
            DemandeCorrection demande, String codeDocument, AuthenticatedUser user) {
        return user != null && user.getRole() == Role.PRESIDENT
                && "LETTRE_ADOPTION".equals(codeDocument)
                && demande != null
                && demande.getStatut() == StatutDemande.EN_VALIDATION;
    }

    /**
     * Lors d'une réclamation acceptée : les versions actives de la lettre d'adoption et des offres corrigées
     * passent en historique (actif = false). Les nouveaux dépôts reprennent la chaîne de versions (n, n+1…).
     */
    @Transactional
    public void archiveAdoptionEtOffresCorrigePourRouverture(Long demandeCorrectionId) {
        for (String code : java.util.List.of(
                "LETTRE_ADOPTION",
                "OFFRE_FISCALE_CORRIGEE",
                "OFFRE_CORRIGEE")) {
            documentRepository.findByDemandeCorrectionIdAndCodeDocumentAndActifTrue(demandeCorrectionId, code)
                    .ifPresent(d -> {
                        d.setActif(false);
                        documentRepository.save(d);
                    });
        }
    }

    @Transactional(readOnly = true)
    public Document findEntityById(Long id) {
        return documentRepository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document non trouvé: " + id));
    }

    private DocumentDto toDto(Document d) {
        return DocumentDto.builder()
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
