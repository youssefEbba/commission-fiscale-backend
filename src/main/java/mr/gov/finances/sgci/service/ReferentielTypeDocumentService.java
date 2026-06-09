package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentCodeValidator;
import mr.gov.finances.sgci.domain.entity.ReferentielTypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.ReferentielTypeDocumentRepository;
import mr.gov.finances.sgci.web.dto.ReferentielTypeDocumentDto;
import mr.gov.finances.sgci.web.dto.UpsertReferentielTypeDocumentRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReferentielTypeDocumentService {

    private final ReferentielTypeDocumentRepository repository;

    @Transactional(readOnly = true)
    public List<ReferentielTypeDocumentDto> findAll(Boolean actifOnly) {
        List<ReferentielTypeDocument> list = Boolean.TRUE.equals(actifOnly)
                ? repository.findByActifTrueOrderByCodeAsc()
                : repository.findAllByOrderByCodeAsc();
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReferentielTypeDocumentDto findByCode(String code) {
        String normalized = requireValidCode(code);
        ReferentielTypeDocument entity = repository.findById(normalized)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Type de document non trouvé: " + normalized));
        return toDto(entity);
    }

    @Transactional
    public ReferentielTypeDocumentDto create(UpsertReferentielTypeDocumentRequest request) {
        String code = requireValidCode(request.getCode());
        if (repository.existsById(code)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Un type de document existe déjà avec le code: " + code);
        }
        ReferentielTypeDocument entity = ReferentielTypeDocument.builder()
                .code(code)
                .libelle(request.getLibelle().trim())
                .libelleAr(request.getLibelleAr() != null ? request.getLibelleAr().trim() : null)
                .actif(request.getActif() == null || Boolean.TRUE.equals(request.getActif()))
                .systeme(Boolean.TRUE.equals(request.getSysteme()))
                .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public ReferentielTypeDocumentDto update(String code, UpsertReferentielTypeDocumentRequest request) {
        String normalized = requireValidCode(code);
        ReferentielTypeDocument entity = repository.findById(normalized)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Type de document non trouvé: " + normalized));
        if (request.getLibelle() != null && !request.getLibelle().isBlank()) {
            entity.setLibelle(request.getLibelle().trim());
        }
        if (request.getLibelleAr() != null) {
            entity.setLibelleAr(request.getLibelleAr().isBlank() ? null : request.getLibelleAr().trim());
        }
        if (request.getActif() != null) {
            entity.setActif(request.getActif());
        }
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(String code) {
        String normalized = requireValidCode(code);
        ReferentielTypeDocument entity = repository.findById(normalized)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Type de document non trouvé: " + normalized));
        if (Boolean.TRUE.equals(entity.getSysteme())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Impossible de supprimer un type système: " + normalized);
        }
        repository.delete(entity);
    }

    /**
     * Crée le type s'il n'existe pas ; retourne le code normalisé.
     */
    @Transactional
    public String ensureExists(String code, String libelle, boolean systeme) {
        String normalized = requireValidCode(code);
        if (repository.existsById(normalized)) {
            return normalized;
        }
        if (libelle == null || libelle.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Libellé obligatoire pour créer le type " + normalized);
        }
        repository.save(ReferentielTypeDocument.builder()
                .code(normalized)
                .libelle(libelle.trim())
                .actif(true)
                .systeme(systeme)
                .build());
        return normalized;
    }

    @Transactional
    public void seedFromEnumIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        for (TypeDocument type : TypeDocument.values()) {
            String code = type.name();
            String libelle = humanizeEnumName(code);
            repository.save(ReferentielTypeDocument.builder()
                    .code(code)
                    .libelle(libelle)
                    .actif(true)
                    .systeme(true)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public void assertActive(String code) {
        String normalized = requireValidCode(code);
        ReferentielTypeDocument entity = repository.findById(normalized)
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Type de document inconnu: " + normalized));
        if (!Boolean.TRUE.equals(entity.getActif())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Type de document inactif: " + normalized);
        }
    }

    private static String requireValidCode(String code) {
        String normalized = DocumentCodeValidator.normalize(code);
        if (!DocumentCodeValidator.isValid(normalized)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Code document invalide (attendu: A-Z, 0-9, _, 2-64 caractères): " + code);
        }
        return normalized;
    }

    private static String humanizeEnumName(String code) {
        return Arrays.stream(code.split("_"))
                .map(part -> part.length() <= 3 ? part : part.charAt(0) + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private ReferentielTypeDocumentDto toDto(ReferentielTypeDocument e) {
        return ReferentielTypeDocumentDto.builder()
                .code(e.getCode())
                .libelle(e.getLibelle())
                .libelleAr(e.getLibelleAr())
                .actif(e.getActif())
                .systeme(e.getSysteme())
                .build();
    }
}
