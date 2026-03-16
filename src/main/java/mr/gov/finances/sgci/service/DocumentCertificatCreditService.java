package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentCertificatCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentCertificatCreditRepository;
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
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentCertificatCreditDto upload(Long certificatCreditId, TypeDocument type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Le fichier est vide");
        }
        requirementValidator.validateUpload(ProcessusDocument.MISE_EN_PLACE_CI, type, file);

        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> new RuntimeException("Certificat de crédit non trouvé: " + certificatCreditId));

        int nextVersion = 1;
        DocumentCertificatCredit previous = repository.findByCertificatCreditIdAndTypeAndActifTrue(certificatCreditId, type)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl;
        try {
            fileUrl = minioService.uploadFile(file);
        } catch (Exception e) {
            throw new RuntimeException("Erreur upload MinIO: " + e.getMessage(), e);
        }

        DocumentCertificatCredit doc = DocumentCertificatCredit.builder()
                .type(type)
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
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentCertificatCreditDto> findByCertificatCreditId(Long certificatCreditId) {
        return repository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TypeDocument> findActiveDocumentTypes(Long certificatCreditId) {
        return repository.findByCertificatCreditIdAndActifTrue(certificatCreditId)
                .stream()
                .map(DocumentCertificatCredit::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentCertificatCreditDto toDto(DocumentCertificatCredit d) {
        return DocumentCertificatCreditDto.builder()
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
