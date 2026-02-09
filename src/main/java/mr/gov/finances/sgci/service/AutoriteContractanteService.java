package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoriteContractanteService {

    private final AutoriteContractanteRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AutoriteContractanteDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AutoriteContractanteDto findById(Long id) {
        return repository.findById(id).map(this::toDto).orElseThrow(
                () -> new RuntimeException("Autorité contractante non trouvée: " + id));
    }

    @Transactional
    public AutoriteContractanteDto create(AutoriteContractanteDto dto) {
        if (dto.getCode() != null && repository.existsByCode(dto.getCode())) {
            throw new RuntimeException("Une autorité contractante avec ce code existe déjà");
        }
        AutoriteContractante entity = AutoriteContractante.builder()
                .nom(dto.getNom())
                .code(dto.getCode())
                .contact(dto.getContact())
                .build();
        entity = repository.save(entity);
        AutoriteContractanteDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "AutoriteContractante", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public AutoriteContractanteDto update(Long id, AutoriteContractanteDto dto) {
        AutoriteContractante entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Autorité contractante non trouvée: " + id));
        entity.setNom(dto.getNom());
        entity.setCode(dto.getCode());
        entity.setContact(dto.getContact());
        entity = repository.save(entity);
        AutoriteContractanteDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "AutoriteContractante", String.valueOf(id), result);
        return result;
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Autorité contractante non trouvée: " + id);
        }
        auditService.log(AuditAction.DELETE, "AutoriteContractante", String.valueOf(id), null);
        repository.deleteById(id);
    }

    private AutoriteContractanteDto toDto(AutoriteContractante e) {
        return AutoriteContractanteDto.builder()
                .id(e.getId())
                .nom(e.getNom())
                .code(e.getCode())
                .contact(e.getContact())
                .build();
    }
}
