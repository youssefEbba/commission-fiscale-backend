package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Devise;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.DeviseRepository;
import mr.gov.finances.sgci.web.dto.DeviseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviseService {

    private final DeviseRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DeviseDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public DeviseDto create(DeviseDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new RuntimeException("Le code devise est obligatoire");
        }
        if (dto.getLibelle() == null || dto.getLibelle().isBlank()) {
            throw new RuntimeException("Le libellé devise est obligatoire");
        }
        String code = dto.getCode().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw new RuntimeException("Une devise avec ce code existe déjà");
        }

        Devise entity = Devise.builder()
                .code(code)
                .libelle(dto.getLibelle())
                .symbole(dto.getSymbole())
                .build();
        entity = repository.save(entity);

        DeviseDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Devise", String.valueOf(entity.getId()), result);
        return result;
    }

    private DeviseDto toDto(Devise d) {
        return DeviseDto.builder()
                .id(d.getId())
                .code(d.getCode())
                .libelle(d.getLibelle())
                .symbole(d.getSymbole())
                .build();
    }
}
