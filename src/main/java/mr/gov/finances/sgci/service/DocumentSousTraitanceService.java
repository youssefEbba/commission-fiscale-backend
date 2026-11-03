package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentSousTraitance;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.repository.DocumentSousTraitanceRepository;
import mr.gov.finances.sgci.repository.SousTraitanceRepository;
import mr.gov.finances.sgci.service.document.DocumentUploadSupport;
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
    private final AuditService auditService;
    private final DocumentUploadSupport documentUploadSupport;

    @Transactional(rollbackFor = Exception.class)
    public DocumentSousTraitanceDto upload(Long sousTraitanceId, String codeDocument, MultipartFile file) throws IOException {
        SousTraitance st = sousTraitanceRepository.findById(sousTraitanceId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + sousTraitanceId));

        DocumentSousTraitance previous = repository.findBySousTraitanceIdAndCodeDocumentAndActifTrue(sousTraitanceId, codeDocument)
                .orElse(null);
        if (previous != null) {
            previous.setActif(false);
        }
        DocumentUploadSupport.StoredUpload stored = documentUploadSupport.validerEtStocker(
                ProcessusDocument.SOUS_TRAITANCE, codeDocument, file,
                previous == null ? null : previous.getVersion());

        DocumentSousTraitance doc = DocumentSousTraitance.builder()
                .codeDocument(codeDocument)
                .nomFichier(stored.nomFichier())
                .chemin(stored.fileUrl())
                .dateUpload(Instant.now())
                .taille(stored.taille())
                .version(stored.version())
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
