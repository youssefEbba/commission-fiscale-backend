package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentCodeValidator;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentRequirementValidator {

    private final DocumentRequirementRepository requirementRepository;
    private final ReferentielTypeDocumentService referentielTypeDocumentService;

    @Transactional(readOnly = true)
    public void validateUpload(ProcessusDocument processus, String codeDocument, MultipartFile file) {
        if (processus == null || codeDocument == null || file == null) {
            return;
        }
        String code = DocumentCodeValidator.normalize(codeDocument);
        if (!DocumentCodeValidator.isValid(code)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Code document invalide");
        }
        referentielTypeDocumentService.assertActive(code);

        List<DocumentRequirement> reqs = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus);
        DocumentRequirement req = reqs.stream()
                .filter(r -> code.equals(r.getCodeDocument()))
                .findFirst()
                .orElse(null);
        if (req == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Type de document non paramétré pour ce processus: " + code + " (" + processus + ")");
        }
        if (req.getTypesAutorises() == null || req.getTypesAutorises().isEmpty()) {
            return;
        }
        TypeFichierAutorise actual = resolveTypeFichierAutorise(file);
        if (actual == null || !req.getTypesAutorises().contains(actual)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Type de fichier non autorisé pour '" + code + "' (autorisé=" + req.getTypesAutorises() + ")");
        }
    }

    @Transactional(readOnly = true)
    public void assertDocumentsConfiguredForProcessus(ProcessusDocument processus, Collection<String> codes) {
        if (processus == null || codes == null || codes.isEmpty()) {
            return;
        }
        List<DocumentRequirement> reqs = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus);
        Set<String> allowed = reqs.stream()
                .map(DocumentRequirement::getCodeDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> invalid = codes.stream()
                .map(DocumentCodeValidator::normalize)
                .filter(Objects::nonNull)
                .filter(c -> !allowed.contains(c))
                .distinct()
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Types de documents non paramétrés pour " + processus + ": " + invalid, invalid);
        }
        for (String code : codes) {
            String normalized = DocumentCodeValidator.normalize(code);
            if (!DocumentCodeValidator.isValid(normalized)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Code document invalide: " + code);
            }
            referentielTypeDocumentService.assertActive(normalized);
        }
    }

    @Transactional(readOnly = true)
    public void assertRequiredDocumentsPresent(ProcessusDocument processus, Collection<String> presentActiveCodes) {
        if (processus == null) {
            return;
        }
        List<DocumentRequirement> reqs = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus);
        if (reqs.isEmpty()) {
            return;
        }
        Set<String> present = presentActiveCodes != null
                ? presentActiveCodes.stream()
                .map(DocumentCodeValidator::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                : Collections.emptySet();

        List<String> missing = reqs.stream()
                .filter(r -> Boolean.TRUE.equals(r.getObligatoire()))
                .map(DocumentRequirement::getCodeDocument)
                .filter(c -> !present.contains(c))
                .distinct()
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Documents obligatoires manquants: " + missing, missing);
        }
    }

    private TypeFichierAutorise resolveTypeFichierAutorise(MultipartFile file) {
        String contentType = file.getContentType();
        String name = file.getOriginalFilename();
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.equals("application/pdf")) {
                return TypeFichierAutorise.PDF;
            }
            if (ct.startsWith("image/")) {
                return TypeFichierAutorise.IMAGE;
            }
            if (ct.contains("msword") || ct.contains("officedocument.wordprocessingml")) {
                return TypeFichierAutorise.WORD;
            }
            if (ct.contains("ms-excel") || ct.contains("officedocument.spreadsheetml")) {
                return TypeFichierAutorise.EXCEL;
            }
        }

        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.endsWith(".pdf")) {
                return TypeFichierAutorise.PDF;
            }
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")) {
                return TypeFichierAutorise.IMAGE;
            }
            if (n.endsWith(".doc") || n.endsWith(".docx")) {
                return TypeFichierAutorise.WORD;
            }
            if (n.endsWith(".xls") || n.endsWith(".xlsx")) {
                return TypeFichierAutorise.EXCEL;
            }
        }
        return null;
    }
}
