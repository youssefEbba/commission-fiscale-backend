package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
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
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentDto upload(Long demandeCorrectionId, TypeDocument type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Le fichier est vide");
        }

        requirementValidator.validateUpload(ProcessusDocument.CORRECTION_OFFRE_FISCALE, type, file);
        DemandeCorrection demande = demandeRepository.findById(demandeCorrectionId)
                .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + demandeCorrectionId));

        int nextVersion = 1;
        Document previous = documentRepository.findByDemandeCorrectionIdAndTypeAndActifTrue(demandeCorrectionId, type)
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

        Document doc = Document.builder()
                .type(type)
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
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> findByDemandeCorrectionId(Long demandeCorrectionId) {
        return documentRepository.findByDemandeCorrectionId(demandeCorrectionId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TypeDocument> findActiveDocumentTypes(Long demandeCorrectionId) {
        return documentRepository.findByDemandeCorrectionIdAndActifTrue(demandeCorrectionId)
                .stream()
                .map(Document::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Document findEntityById(Long id) {
        return documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Document non trouvé: " + id));
    }

    private DocumentDto toDto(Document d) {
        return DocumentDto.builder()
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
