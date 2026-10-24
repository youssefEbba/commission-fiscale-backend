package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentCodeValidator;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.document.PiecesDuCircuit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.UpsertDocumentRequirementRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentRequirementService {

    private final DocumentRequirementRepository repository;
    private final ReferentielTypeDocumentService referentielTypeDocumentService;
    private final AuditService auditService;

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
     *
     * <p>Une pièce <b>supprimée</b> n'apparaît nulle part. Une pièce <b>désactivée</b> reste à l'écran
     * de paramétrage, marquée inactive pour pouvoir être réactivée, et quitte le formulaire de dépôt.
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
                .filter(r -> !r.estSupprime())
                .filter(r -> !depotSeulement || r.estActif())
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
        DocumentRequirement existante = repository.findByProcessusAndCodeDocument(request.getProcessus(), code)
                .orElse(null);
        if (existante != null && !existante.estSupprime()) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Exigence déjà définie pour " + request.getProcessus() + " / " + code);
        }
        // Une pièce supprimée garde sa ligne : la recréer, c'est la restaurer avec les nouvelles
        // valeurs. Une seconde ligne buterait sur l'unicité (processus, code).
        DocumentRequirement entity = existante != null ? existante : new DocumentRequirement();
        entity.setDateSuppression(null);
        entity.setActif(true);
        applyRequest(entity, request, code);
        entity = repository.save(entity);
        DocumentRequirementDto result = toDto(entity);
        auditService.log(existante != null ? AuditAction.UPDATE : AuditAction.CREATE,
                "DocumentRequirement", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public DocumentRequirementDto update(Long id, UpsertDocumentRequirementRequest request) {
        DocumentRequirement entity = trouverNonSupprimee(id);
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

    /**
     * Retire une pièce d'un processus.
     *
     * <p>La ligne est marquée supprimée, pas effacée : effacée, le seed la recréait au démarrage
     * suivant, et une pièce de sortie perdait son sens — ses exemplaires déjà déposés seraient
     * devenus visibles du déposant avant la fin du processus. Voir
     * {@link DocumentRequirement#getDateSuppression()}.
     */
    @Transactional
    public void delete(Long id) {
        DocumentRequirement entity = trouverNonSupprimee(id);
        assertRetirable(entity, "supprimée");
        entity.setDateSuppression(Instant.now());
        entity.setActif(false);
        repository.save(entity);
        auditService.log(AuditAction.DELETE, "DocumentRequirement", String.valueOf(id),
                java.util.Map.of("processus", entity.getProcessus().name(), "codeDocument", entity.getCodeDocument()));
    }

    /**
     * Active ou désactive une pièce dans un processus. Réversible.
     *
     * <p>Désactivée, la pièce n'est plus proposée au dépôt, plus exigée, et ne peut plus être
     * réclamée par une demande de pièce. Les exemplaires déjà déposés restent consultables, et la
     * pièce reste à l'écran de paramétrage pour être réactivée.
     */
    @Transactional
    public DocumentRequirementDto changerActivation(Long id, boolean actif) {
        DocumentRequirement entity = trouverNonSupprimee(id);
        if (!actif) {
            assertRetirable(entity, "désactivée");
        }
        entity.setActif(actif);
        entity = repository.save(entity);
        DocumentRequirementDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DocumentRequirement", String.valueOf(id), result);
        return result;
    }

    private DocumentRequirement trouverNonSupprimee(Long id) {
        return repository.findById(id)
                .filter(r -> !r.estSupprime())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document requirement non trouvé: " + id));
    }

    /** Une pièce exigée par le circuit ne se retire pas : l'étape qui l'attend resterait bloquée. */
    private static void assertRetirable(DocumentRequirement entity, String participe) {
        if (PiecesDuCircuit.exigeeParLeCircuit(entity.getCodeDocument())) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cette pièce ne peut pas être " + participe + " : le circuit l'exige ("
                            + entity.getCodeDocument() + ")");
        }
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
        // Null accepté : il signifie « non tranché », et se lit partout comme une pièce d'entrée.
        entity.setSens(request.getSens());
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
                .sens(e.sensEffectif())
                .deposableParLeDemandeur(DocumentDepotPolicy.deposableParLeDemandeur(e.getCodeDocument()))
                .actif(e.estActif())
                .pieceDuCircuit(PiecesDuCircuit.exigeeParLeCircuit(e.getCodeDocument()))
                .build();
    }
}
