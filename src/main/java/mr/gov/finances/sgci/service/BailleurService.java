package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.web.dto.BailleurDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BailleurService {

    private final BailleurRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BailleurDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public BailleurDto create(BailleurDto dto) {
        if (dto.getNom() == null || dto.getNom().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le nom du bailleur est obligatoire");
        }
        if (repository.existsByNom(dto.getNom())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Un bailleur avec ce nom existe déjà");
        }

        Bailleur entity = Bailleur.builder()
                .nom(dto.getNom())
                .details(dto.getDetails())
                .build();
        entity = repository.save(entity);

        BailleurDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Bailleur", String.valueOf(entity.getId()), result);
        return result;
    }

    private BailleurDto toDto(Bailleur b) {
        return BailleurDto.builder()
                .id(b.getId())
                .nom(b.getNom())
                .details(b.getDetails())
                .build();
    }
}
