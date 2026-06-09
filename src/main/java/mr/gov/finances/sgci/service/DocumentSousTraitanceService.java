package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentSousTraitance;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DocumentSousTraitanceRepository;
import mr.gov.finances.sgci.repository.SousTraitanceRepository;
import mr.gov.finances.sgci.web.dto.DocumentSousTraitanceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentSousTraitanceService {

    private final DocumentSousTraitanceRepository repository;
    private final SousTraitanceRepository sousTraitanceRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentSousTraitanceDto upload(Long sousTraitanceId, String codeDocument, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        requirementValidator.validateUpload(ProcessusDocument.SOUS_TRAITANCE, codeDocument, file);

        SousTraitance st = sousTraitanceRepository.findById(sousTraitanceId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + sousTraitanceId));

        int nextVersion = 1;
        DocumentSousTraitance previous = repository.findBySousTraitanceIdAndCodeDocumentAndActifTrue(sousTraitanceId, codeDocument)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentSousTraitance doc = DocumentSousTraitance.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .sousTraitance(st)
                .build();

        doc = repository.save(doc);
        DocumentSousTraitanceDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentSousTraitance", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentSousTraitanceDto> findBySousTraitanceId(Long sousTraitanceId) {
        return repository.findBySousTraitanceId(sousTraitanceId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> findActiveDocumentTypes(Long sousTraitanceId) {
        return repository.findBySousTraitanceIdAndActifTrue(sousTraitanceId)
                .stream()
                .map(DocumentSousTraitance::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentSousTraitanceDto toDto(DocumentSousTraitance d) {
        return DocumentSousTraitanceDto.builder()
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
