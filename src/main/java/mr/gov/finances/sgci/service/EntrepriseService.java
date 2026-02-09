package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntrepriseService {

    private final EntrepriseRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<EntrepriseDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EntrepriseDto findById(Long id) {
        return repository.findById(id).map(this::toDto).orElseThrow(
                () -> new RuntimeException("Entreprise non trouvée: " + id));
    }

    @Transactional
    public EntrepriseDto create(EntrepriseDto dto) {
        if (dto.getNif() != null && repository.existsByNif(dto.getNif())) {
            throw new RuntimeException("Une entreprise avec ce NIF existe déjà");
        }
        Entreprise entity = Entreprise.builder()
                .raisonSociale(dto.getRaisonSociale())
                .nif(dto.getNif())
                .adresse(dto.getAdresse())
                .situationFiscale(dto.getSituationFiscale())
                .build();
        entity = repository.save(entity);
        EntrepriseDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Entreprise", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public EntrepriseDto update(Long id, EntrepriseDto dto) {
        Entreprise entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Entreprise non trouvée: " + id));
        entity.setRaisonSociale(dto.getRaisonSociale());
        entity.setNif(dto.getNif());
        entity.setAdresse(dto.getAdresse());
        entity.setSituationFiscale(dto.getSituationFiscale());
        entity = repository.save(entity);
        EntrepriseDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "Entreprise", String.valueOf(id), result);
        return result;
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Entreprise non trouvée: " + id);
        }
        auditService.log(AuditAction.DELETE, "Entreprise", String.valueOf(id), null);
        repository.deleteById(id);
    }

    private EntrepriseDto toDto(Entreprise e) {
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .situationFiscale(e.getSituationFiscale())
                .build();
    }
}
