package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ReferentielTaxe;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.ReferentielTaxeRepository;
import mr.gov.finances.sgci.web.dto.CreateReferentielTaxeRequest;
import mr.gov.finances.sgci.web.dto.ReferentielTaxeDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReferentielTaxeService {

    private final ReferentielTaxeRepository repository;
    private final AuditService auditService;

    /** Retourne toutes les taxes actives — pour les formulaires entreprise. */
    @Transactional(readOnly = true)
    public List<ReferentielTaxeDto> findActives() {
        return repository.findByActiveTrueOrderByOrdreAffichageAscCodeTaxeAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Retourne toutes les taxes (actives + inactives) — pour la console admin. */
    @Transactional(readOnly = true)
    public List<ReferentielTaxeDto> findAll() {
        return repository.findAllByOrderByOrdreAffichageAscCodeTaxeAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReferentielTaxeDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public ReferentielTaxeDto create(CreateReferentielTaxeRequest request) {
        if (repository.existsByCodeTaxe(request.getCodeTaxe().trim().toUpperCase())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une taxe avec le code '" + request.getCodeTaxe() + "' existe déjà");
        }
        ReferentielTaxe taxe = ReferentielTaxe.builder()
                .codeTaxe(request.getCodeTaxe().trim().toUpperCase())
                .denominationTaxe(request.getDenominationTaxe().trim())
                .valeurTaxe(request.getValeurTaxe())
                .ordreAffichage(request.getOrdreAffichage() != null ? request.getOrdreAffichage() : 0)
                .active(request.getActive() != null ? request.getActive() : Boolean.TRUE)
                .dateCreation(Instant.now())
                .build();
        taxe = repository.save(taxe);
        ReferentielTaxeDto dto = toDto(taxe);
        auditService.log(AuditAction.CREATE, "ReferentielTaxe", String.valueOf(taxe.getId()), dto);
        return dto;
    }

    @Transactional
    public ReferentielTaxeDto update(Long id, CreateReferentielTaxeRequest request) {
        ReferentielTaxe taxe = getOrThrow(id);
        String newCode = request.getCodeTaxe().trim().toUpperCase();
        if (!taxe.getCodeTaxe().equals(newCode)
                && repository.existsByCodeTaxeAndIdNot(newCode, id)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une autre taxe avec le code '" + newCode + "' existe déjà");
        }
        taxe.setCodeTaxe(newCode);
        taxe.setDenominationTaxe(request.getDenominationTaxe().trim());
        taxe.setValeurTaxe(request.getValeurTaxe());
        if (request.getOrdreAffichage() != null) {
            taxe.setOrdreAffichage(request.getOrdreAffichage());
        }
        if (request.getActive() != null) {
            taxe.setActive(request.getActive());
        }
        taxe.setDateModification(Instant.now());
        taxe = repository.save(taxe);
        ReferentielTaxeDto dto = toDto(taxe);
        auditService.log(AuditAction.UPDATE, "ReferentielTaxe", String.valueOf(taxe.getId()), dto);
        return dto;
    }

    /** Désactivation logique (soft delete) — préserve les données historiques. */
    @Transactional
    public ReferentielTaxeDto desactiver(Long id) {
        ReferentielTaxe taxe = getOrThrow(id);
        taxe.setActive(false);
        taxe.setDateModification(Instant.now());
        taxe = repository.save(taxe);
        ReferentielTaxeDto dto = toDto(taxe);
        auditService.log(AuditAction.UPDATE, "ReferentielTaxe", String.valueOf(taxe.getId()), dto);
        return dto;
    }

    /** Réactivation d'une taxe désactivée. */
    @Transactional
    public ReferentielTaxeDto activer(Long id) {
        ReferentielTaxe taxe = getOrThrow(id);
        taxe.setActive(true);
        taxe.setDateModification(Instant.now());
        taxe = repository.save(taxe);
        ReferentielTaxeDto dto = toDto(taxe);
        auditService.log(AuditAction.UPDATE, "ReferentielTaxe", String.valueOf(taxe.getId()), dto);
        return dto;
    }

    /** Suppression physique — uniquement si la taxe n'est pas utilisée. */
    @Transactional
    public void delete(Long id) {
        ReferentielTaxe taxe = getOrThrow(id);
        repository.delete(taxe);
        auditService.log(AuditAction.DELETE, "ReferentielTaxe", String.valueOf(id), null);
    }

    private ReferentielTaxe getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Taxe non trouvée: " + id));
    }

    private ReferentielTaxeDto toDto(ReferentielTaxe t) {
        return ReferentielTaxeDto.builder()
                .id(t.getId())
                .codeTaxe(t.getCodeTaxe())
                .denominationTaxe(t.getDenominationTaxe())
                .valeurTaxe(t.getValeurTaxe())
                .ordreAffichage(t.getOrdreAffichage())
                .active(t.getActive())
                .dateCreation(t.getDateCreation())
                .dateModification(t.getDateModification())
                .build();
    }
}
