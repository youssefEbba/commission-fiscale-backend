package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.UpsertDocumentRequirementRequest;
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

    @Transactional(readOnly = true)
    public List<DocumentRequirementDto> findByProcessus(ProcessusDocument processus) {
        List<DocumentRequirement> direct = repository.findByProcessusOrderByOrdreAffichageAsc(processus);
        if (direct.isEmpty()) {
            ProcessusDocument fallback = resolveFallback(processus);
            if (fallback != null) {
                direct = repository.findByProcessusOrderByOrdreAffichageAsc(fallback);
            }
        }
        return direct
                .stream()
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
        DocumentRequirement entity = new DocumentRequirement();
        applyRequest(entity, request);
        entity = repository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public DocumentRequirementDto update(Long id, UpsertDocumentRequirementRequest request) {
        DocumentRequirement entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document requirement non trouvé: " + id));
        applyRequest(entity, request);
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

    private void applyRequest(DocumentRequirement entity, UpsertDocumentRequirementRequest request) {
        entity.setProcessus(request.getProcessus());
        entity.setTypeDocument(request.getTypeDocument());
        entity.setObligatoire(Boolean.TRUE.equals(request.getObligatoire()));
        Set<?> types = request.getTypesAutorises();
        if (types == null) {
            entity.setTypesAutorises(EnumSet.noneOf(mr.gov.finances.sgci.domain.enums.TypeFichierAutorise.class));
        } else {
            entity.setTypesAutorises(EnumSet.copyOf(request.getTypesAutorises()));
        }
        entity.setDescription(request.getDescription());
        entity.setOrdreAffichage(request.getOrdreAffichage());
    }

    private DocumentRequirementDto toDto(DocumentRequirement e) {
        return DocumentRequirementDto.builder()
                .id(e.getId())
                .processus(e.getProcessus())
                .typeDocument(e.getTypeDocument())
                .obligatoire(e.getObligatoire())
                .typesAutorises(e.getTypesAutorises())
                .description(e.getDescription())
                .ordreAffichage(e.getOrdreAffichage())
                .build();
    }
}
