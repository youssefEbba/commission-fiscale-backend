package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentTransfertCredit;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DocumentTransfertCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.web.dto.DocumentTransfertCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTransfertCreditService {

    private final DocumentTransfertCreditRepository repository;
    private final TransfertCreditRepository transfertRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentTransfertCreditDto upload(Long transfertCreditId, TypeDocument type, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Le fichier est vide");
        }
        requirementValidator.validateUpload(ProcessusDocument.TRANSFERT_CREDIT, type, file);

        TransfertCredit transfert = transfertRepository.findById(transfertCreditId)
                .orElseThrow(() -> new RuntimeException("Transfert de crédit non trouvé: " + transfertCreditId));

        int nextVersion = 1;
        DocumentTransfertCredit previous = repository.findByTransfertCreditIdAndTypeAndActifTrue(transfertCreditId, type)
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

        DocumentTransfertCredit doc = DocumentTransfertCredit.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .transfertCredit(transfert)
                .build();

        doc = repository.save(doc);
        DocumentTransfertCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentTransfertCredit", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentTransfertCreditDto> findByTransfertCreditId(Long transfertCreditId) {
        return repository.findByTransfertCreditId(transfertCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TypeDocument> findActiveDocumentTypes(Long transfertCreditId) {
        return repository.findByTransfertCreditIdAndActifTrue(transfertCreditId)
                .stream()
                .map(DocumentTransfertCredit::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentTransfertCreditDto toDto(DocumentTransfertCredit d) {
        return DocumentTransfertCreditDto.builder()
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
