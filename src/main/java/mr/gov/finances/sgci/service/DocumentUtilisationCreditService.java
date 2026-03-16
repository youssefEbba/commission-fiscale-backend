package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentUtilisationCreditService {

    private final DocumentUtilisationCreditRepository repository;
    private final UtilisationCreditRepository utilisationRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;

    @Transactional
    public DocumentUtilisationCreditDto upload(Long utilisationCreditId, TypeDocument type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Le fichier est vide");
        }
        UtilisationCredit utilisation = utilisationRepository.findById(utilisationCreditId)
                .orElseThrow(() -> new RuntimeException("Utilisation de crédit non trouvée: " + utilisationCreditId));

        ProcessusDocument processus = resolveProcessus(utilisation);
        requirementValidator.validateUpload(processus, type, file);

        int nextVersion = 1;
        DocumentUtilisationCredit previous = repository.findByUtilisationCreditIdAndTypeAndActifTrue(utilisationCreditId, type)
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

        DocumentUtilisationCredit doc = DocumentUtilisationCredit.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .utilisationCredit(utilisation)
                .build();
        doc = repository.save(doc);
        DocumentUtilisationCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentUtilisationCredit", String.valueOf(doc.getId()), result);
        return result;
    }

    private ProcessusDocument resolveProcessus(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (utilisation instanceof UtilisationTVAInterieure t && t.getTypeAchat() != null) {
                return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
            }
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return ProcessusDocument.UTILISATION_CI;
    }

    @Transactional(readOnly = true)
    public List<DocumentUtilisationCreditDto> findByUtilisationCreditId(Long utilisationCreditId) {
        return repository.findByUtilisationCreditId(utilisationCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TypeDocument> findActiveDocumentTypes(Long utilisationCreditId) {
        return repository.findByUtilisationCreditIdAndActifTrue(utilisationCreditId)
                .stream()
                .map(DocumentUtilisationCredit::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentUtilisationCreditDto toDto(DocumentUtilisationCredit d) {
        return DocumentUtilisationCreditDto.builder()
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
