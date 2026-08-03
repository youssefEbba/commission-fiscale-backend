package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.entity.DocumentCertificatCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentCertificatCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentCertificatCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCertificatCreditService {

    private final DocumentCertificatCreditRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final DecisionCertificatCreditRepository decisionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;
    private final RejetTempResponseService rejetTempResponseService;

    @Transactional
    public DocumentCertificatCreditDto upload(Long certificatCreditId, String codeDocument, String message, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        requirementValidator.validateUpload(ProcessusDocument.MISE_EN_PLACE_CI, codeDocument, file);

        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + certificatCreditId));

        int nextVersion = 1;
        DocumentCertificatCredit previous = repository.findByCertificatCreditIdAndCodeDocumentAndActifTrue(certificatCreditId, codeDocument)
                .orElse(null);
        if (previous != null) {
            assertReplacementAllowed(certificat, codeDocument, user);
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        boolean askedByOpenRejetTemp = decisionRepository.findByCertificatCreditIdAndDecisionAndRejetTempStatus(
                        certificat.getId(),
                        DecisionCorrectionType.REJET_TEMP,
                        RejetTempStatus.OUVERT
                ).stream().anyMatch(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument));

        if (askedByOpenRejetTemp && (message == null || message.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentCertificatCredit doc = DocumentCertificatCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .certificatCredit(certificat)
                .build();

        doc = repository.save(doc);
        DocumentCertificatCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentCertificatCredit", String.valueOf(doc.getId()), result);

        if (askedByOpenRejetTemp) {
            rejetTempResponseService.recordCertificatUploadResponse(certificat.getId(), codeDocument, message, doc, user);
        }

        return result;
    }

    /**
     * Remplacement d'un document par un administrateur (ADMIN_SI), à tout moment quel que soit le
     * statut du certificat. Motif obligatoire, journalisé dans l'audit sous
     * {@link AuditAction#ADMIN_CORRECTION}. L'ancienne version est désactivée (historique
     * conservé), pas supprimée.
     */
    @Transactional
    public DocumentCertificatCreditDto adminReplace(Long certificatCreditId, String codeDocument, String motif, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Correction administrateur réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de la correction administrateur est obligatoire");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + certificatCreditId));

        DocumentCertificatCredit previous = repository.findByCertificatCreditIdAndCodeDocumentAndActifTrue(certificatCreditId, codeDocument)
                .orElse(null);
        int nextVersion = 1;
        if (previous != null) {
            previous.setActif(false);
            repository.save(previous);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentCertificatCredit doc = DocumentCertificatCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .certificatCredit(certificat)
                .build();
        doc = repository.save(doc);
        DocumentCertificatCreditDto result = toDto(doc);
        auditService.log(AuditAction.ADMIN_CORRECTION, "DocumentCertificatCredit", String.valueOf(doc.getId()), result, motif);
        return result;
    }

    private void assertReplacementAllowed(CertificatCredit certificat, String codeDocument, AuthenticatedUser user) {
        if (certificat == null || certificat.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Remplacement interdit: réservé à l'Autorité Contractante");
        }
        if (certificat.getStatut() != StatutCertificat.INCOMPLETE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: le certificat n'est pas en statut INCOMPLETE");
        }
        boolean asked = decisionRepository.findByCertificatCreditId(certificat.getId()).stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT
                        && d.getDocumentsDemandes() != null
                        && d.getDocumentsDemandes().contains(codeDocument));
        if (!asked) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: aucun acteur n'a demandé ce document");
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentCertificatCreditDto> findByCertificatCreditId(Long certificatCreditId) {
        return repository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> findActiveDocumentTypes(Long certificatCreditId) {
        return repository.findByCertificatCreditIdAndActifTrue(certificatCreditId)
                .stream()
                .map(DocumentCertificatCredit::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentCertificatCreditDto toDto(DocumentCertificatCredit d) {
        return DocumentCertificatCreditDto.builder()
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
