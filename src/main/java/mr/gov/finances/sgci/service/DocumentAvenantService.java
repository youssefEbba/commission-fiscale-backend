package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.DocumentAvenant;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.DocumentAvenantRepository;
import mr.gov.finances.sgci.web.dto.DocumentAvenantDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentAvenantService {

    private final DocumentAvenantRepository repository;
    private final AvenantRepository avenantRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentAvenantDto upload(Long avenantId, TypeDocument type, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        requirementValidator.validateUpload(ProcessusDocument.MODIFICATION_CI, type, file);

        Avenant avenant = avenantRepository.findById(avenantId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Avenant non trouvé: " + avenantId));

        int nextVersion = 1;
        DocumentAvenant previous = repository.findByAvenantIdAndTypeAndActifTrue(avenantId, type)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentAvenant doc = DocumentAvenant.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .avenant(avenant)
                .build();
        doc = repository.save(doc);

        DocumentAvenantDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentAvenant", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentAvenantDto> findByAvenantId(Long avenantId) {
        return repository.findByAvenantId(avenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DocumentAvenantDto toDto(DocumentAvenant d) {
        return DocumentAvenantDto.builder()
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
