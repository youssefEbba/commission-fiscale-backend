package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentCodeValidator;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.UpsertDocumentRequirementRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentRequirementService {

    private final DocumentRequirementRepository repository;
    private final ReferentielTypeDocumentService referentielTypeDocumentService;

    @Transactional(readOnly = true)
    public List<DocumentRequirementDto> findByProcessus(ProcessusDocument processus) {
        return findByProcessus(processus, false);
    }

    /**
     * Les pièces paramétrées d'un processus, éventuellement réduites à ce que le déposant apporte.
     *
     * <p>{@code depotSeulement} est explicite et non déduit du rôle : l'écran de paramétrage doit
     * continuer à voir <em>tous</em> les codes, sans quoi un administrateur croirait un type disparu
     * et le recréerait en double. Le formulaire de dépôt, lui, demande la liste filtrée.
     */
    public List<DocumentRequirementDto> findByProcessus(ProcessusDocument processus, boolean depotSeulement) {
        List<DocumentRequirement> direct = repository.findByProcessusOrderByOrdreAffichageAsc(processus);
        if (direct.isEmpty()) {
            ProcessusDocument fallback = resolveFallback(processus);
            if (fallback != null) {
                direct = repository.findByProcessusOrderByOrdreAffichageAsc(fallback);
            }
        }
        return direct.stream()
                .filter(r -> !depotSeulement || DocumentDepotPolicy.deposableParLeDemandeur(r.getCodeDocument()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ProcessusDocument resolveFallback(ProcessusDocument processus) {
        if (processus == null) {
            return null;
        }
        if (processus == ProcessusDocument.UTILISATION_CI_EXTERIEUR) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (processus == ProcessusDocument.UTILISATION_CI_INTERIEUR) {
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return null;
    }

    @Transactional
    public DocumentRequirementDto create(UpsertDocumentRequirementRequest request) {
        String code = resolveCodeForRequirement(request);
        if (repository.existsByProcessusAndCodeDocument(request.getProcessus(), code)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Exigence déjà définie pour " + request.getProcessus() + " / " + code);
        }
        DocumentRequirement entity = new DocumentRequirement();
        applyRequest(entity, request, code);
        entity = repository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public DocumentRequirementDto update(Long id, UpsertDocumentRequirementRequest request) {
        DocumentRequirement entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document requirement non trouvé: " + id));
        String code = resolveCodeForRequirement(request);
        if (!code.equals(entity.getCodeDocument())
                && repository.existsByProcessusAndCodeDocument(request.getProcessus(), code)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Exigence déjà définie pour " + request.getProcessus() + " / " + code);
        }
        applyRequest(entity, request, code);
        entity = repository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document requirement non trouvé: " + id);
        }
        repository.deleteById(id);
    }

    private String resolveCodeForRequirement(UpsertDocumentRequirementRequest request) {
        String code = DocumentCodeValidator.normalize(request.getCodeDocument());
        if (!DocumentCodeValidator.isValid(code)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Code document invalide");
        }
        referentielTypeDocumentService.ensureExists(code,
                request.getLibelle() != null ? request.getLibelle() : code,
                false);
        referentielTypeDocumentService.assertActive(code);
        return code;
    }

    private void applyRequest(DocumentRequirement entity, UpsertDocumentRequirementRequest request, String code) {
        entity.setProcessus(request.getProcessus());
        entity.setCodeDocument(code);
        entity.setObligatoire(Boolean.TRUE.equals(request.getObligatoire()));
        Set<TypeFichierAutorise> types = request.getTypesAutorises();
        if (types == null) {
            entity.setTypesAutorises(EnumSet.noneOf(TypeFichierAutorise.class));
        } else {
            entity.setTypesAutorises(EnumSet.copyOf(types));
        }
        entity.setDescription(request.getDescription());
        entity.setOrdreAffichage(request.getOrdreAffichage());
    }

    private DocumentRequirementDto toDto(DocumentRequirement e) {
        String libelle = referentielTypeDocumentService.findByCode(e.getCodeDocument()).getLibelle();
        return DocumentRequirementDto.builder()
                .id(e.getId())
                .processus(e.getProcessus())
                .codeDocument(e.getCodeDocument())
                .libelle(libelle)
                .obligatoire(e.getObligatoire())
                .typesAutorises(e.getTypesAutorises())
                .description(e.getDescription())
                .ordreAffichage(e.getOrdreAffichage())
                .deposableParLeDemandeur(DocumentDepotPolicy.deposableParLeDemandeur(e.getCodeDocument()))
                .build();
    }
}
