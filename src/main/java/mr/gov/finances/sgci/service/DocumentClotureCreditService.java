package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.entity.DocumentClotureCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.repository.ClotureCreditRepository;
import mr.gov.finances.sgci.repository.DocumentClotureCreditRepository;
import mr.gov.finances.sgci.web.dto.DocumentClotureCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentClotureCreditService {

    private final DocumentClotureCreditRepository repository;
    private final ClotureCreditRepository clotureCreditRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentClotureCreditDto upload(Long clotureCreditId, String codeDocument, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        requirementValidator.validateUpload(ProcessusDocument.CLOTURE_CI, codeDocument, file);

        ClotureCredit cloture = clotureCreditRepository.findById(clotureCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Clôture crédit non trouvée: " + clotureCreditId));

        int nextVersion = 1;
        DocumentClotureCredit previous = repository.findByClotureCreditIdAndCodeDocumentAndActifTrue(clotureCreditId, codeDocument)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentClotureCredit doc = DocumentClotureCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .clotureCredit(cloture)
                .build();
        doc = repository.save(doc);

        DocumentClotureCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentClotureCredit", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentClotureCreditDto> findByClotureCreditId(Long clotureCreditId) {
        return repository.findByClotureCreditId(clotureCreditId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DocumentClotureCreditDto toDto(DocumentClotureCredit d) {
        return DocumentClotureCreditDto.builder()
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
