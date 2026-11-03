package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.DocumentAvenant;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.DocumentAvenantRepository;
import mr.gov.finances.sgci.service.document.DocumentUploadSupport;
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
    private final AuditService auditService;
    private final DocumentUploadSupport documentUploadSupport;

    @Transactional
    public DocumentAvenantDto upload(Long avenantId, String codeDocument, MultipartFile file) throws IOException {
        Avenant avenant = avenantRepository.findById(avenantId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Avenant non trouvé: " + avenantId));

        DocumentAvenant previous = repository.findByAvenantIdAndCodeDocumentAndActifTrue(avenantId, codeDocument)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
        }
        DocumentUploadSupport.StoredUpload stored = documentUploadSupport.validerEtStocker(
                ProcessusDocument.MODIFICATION_CI, codeDocument, file,
                previous == null ? null : previous.getVersion());

        DocumentAvenant doc = DocumentAvenant.builder()
                .codeDocument(codeDocument)
                .nomFichier(stored.nomFichier())
                .chemin(stored.fileUrl())
                .dateUpload(Instant.now())
                .taille(stored.taille())
                .version(stored.version())
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
