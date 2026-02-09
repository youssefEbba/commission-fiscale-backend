package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRepository;
import mr.gov.finances.sgci.web.dto.DocumentDto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final AuditService auditService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional
    public DocumentDto upload(Long demandeCorrectionId, TypeDocument type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Le fichier est vide");
        }
        DemandeCorrection demande = demandeRepository.findById(demandeCorrectionId)
                .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + demandeCorrectionId));

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')) : "";
        String storedFilename = UUID.randomUUID().toString() + ext;
        Path targetPath = dir.resolve(storedFilename);
        file.transferTo(targetPath.toFile());

        Document doc = Document.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : storedFilename)
                .chemin(targetPath.toString())
                .dateUpload(Instant.now())
                .taille(file.getSize())
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
                .build();
    }
}
