package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentRequirementValidator {

    private final DocumentRequirementRepository requirementRepository;

    @Transactional(readOnly = true)
    public void validateUpload(ProcessusDocument processus, TypeDocument typeDocument, MultipartFile file) {
        if (processus == null || typeDocument == null || file == null) {
            return;
        }
        List<DocumentRequirement> reqs = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus);
        if (reqs.isEmpty()) {
            return;
        }
        DocumentRequirement req = reqs.stream()
                .filter(r -> r.getTypeDocument() == typeDocument)
                .findFirst()
                .orElse(null);
        if (req == null) {
            return;
        }
        if (req.getTypesAutorises() == null || req.getTypesAutorises().isEmpty()) {
            return;
        }
        TypeFichierAutorise actual = resolveTypeFichierAutorise(file);
        if (actual == null || !req.getTypesAutorises().contains(actual)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Type de fichier non autorisé pour '" + typeDocument + "' (autorisé=" + req.getTypesAutorises() + ")");
        }
    }

    @Transactional(readOnly = true)
    public void assertRequiredDocumentsPresent(ProcessusDocument processus, Collection<TypeDocument> presentActiveTypes) {
        if (processus == null) {
            return;
        }
        List<DocumentRequirement> reqs = requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus);
        if (reqs.isEmpty()) {
            return;
        }
        Set<TypeDocument> present = presentActiveTypes != null
                ? new HashSet<>(presentActiveTypes)
                : Collections.emptySet();

        List<TypeDocument> missing = reqs.stream()
                .filter(r -> Boolean.TRUE.equals(r.getObligatoire()))
                .map(DocumentRequirement::getTypeDocument)
                .filter(t -> !present.contains(t))
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
