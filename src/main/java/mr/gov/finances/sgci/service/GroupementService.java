package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Groupement;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.GroupementRepository;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.GroupementDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupementService {

    private final GroupementRepository groupementRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<GroupementDto> findAll(Boolean actifsSeulement) {
        List<Groupement> list = Boolean.TRUE.equals(actifsSeulement)
                ? groupementRepository.findByActifTrueOrderByRaisonSocialeAsc()
                : groupementRepository.findAllByOrderByRaisonSocialeAsc();
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupementDto findById(Long id) {
        Groupement g = groupementRepository.findByIdWithMembres(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Groupement non trouvé: " + id));
        return toDto(g);
    }

    @Transactional
    public GroupementDto create(GroupementDto dto) {
        Set<Entreprise> membres = resolveMembres(dto.getMembreIds());
        Entreprise chef = resolveChefDeFile(dto.getChefDeFileId(), membres);

        Groupement entity = Groupement.builder()
                .raisonSociale(dto.getRaisonSociale())
                .nomCommercial(dto.getNomCommercial())
                .adresse(dto.getAdresse())
                .autre(dto.getAutre())
                .situationFiscale(dto.getSituationFiscale())
                .actif(dto.isActif())
                .chefDeFile(chef)
                .membres(membres)
                .build();
        entity = groupementRepository.save(entity);
        GroupementDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Groupement", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public GroupementDto update(Long id, GroupementDto dto) {
        Groupement entity = groupementRepository.findByIdWithMembres(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Groupement non trouvé: " + id));

        Set<Entreprise> membres = resolveMembres(dto.getMembreIds());
        Entreprise chef = resolveChefDeFile(dto.getChefDeFileId(), membres);

        entity.setRaisonSociale(dto.getRaisonSociale());
        entity.setNomCommercial(dto.getNomCommercial());
        entity.setAdresse(dto.getAdresse());
        entity.setAutre(dto.getAutre());
        entity.setSituationFiscale(dto.getSituationFiscale());
        entity.setActif(dto.isActif());
        entity.setChefDeFile(chef);
        entity.getMembres().clear();
        entity.getMembres().addAll(membres);

        entity = groupementRepository.save(entity);
        GroupementDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "Groupement", String.valueOf(id), result);
        return result;
    }

    @Transactional
    public void deleteById(Long id) {
        Groupement entity = groupementRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Groupement non trouvé: " + id));
        if (demandeCorrectionRepository.existsByGroupementId(id)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Suppression impossible : des demandes de correction sont liées à ce groupement");
        }
        auditService.log(AuditAction.DELETE, "Groupement", String.valueOf(id), null);
        groupementRepository.delete(entity);
    }

    private Set<Entreprise> resolveMembres(List<Long> membreIds) {
        if (membreIds == null || membreIds.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le groupement doit comporter au moins un membre");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(membreIds);
        if (uniqueIds.size() < 2) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Un groupement doit comporter au moins deux entreprises (chef de file + partenaire)");
        }
        List<Entreprise> found = entrepriseRepository.findAllById(uniqueIds);
        if (found.size() != uniqueIds.size()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Un ou plusieurs membres sont introuvables");
        }
        return new HashSet<>(found);
    }

    private Entreprise resolveChefDeFile(Long chefDeFileId, Set<Entreprise> membres) {
        if (chefDeFileId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le chef de file est obligatoire");
        }
        Entreprise chef = membres.stream()
                .filter(m -> chefDeFileId.equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le chef de file doit faire partie des membres du groupement"));
        if (chef.getNif() == null || chef.getNif().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le chef de file doit disposer d'un NIF (le NIF du groupement en est dérivé)");
        }
        return chef;
    }

    private GroupementDto toDto(Groupement g) {
        Entreprise chef = g.getChefDeFile();
        List<Long> membreIds = g.getMembres() != null
                ? g.getMembres().stream().map(Entreprise::getId).sorted().collect(Collectors.toList())
                : List.of();
        List<EntrepriseDto> membres = g.getMembres() != null
                ? g.getMembres().stream().map(this::toEntrepriseLite).collect(Collectors.toList())
                : List.of();
        return GroupementDto.builder()
                .id(g.getId())
                .raisonSociale(g.getRaisonSociale())
                .nomCommercial(g.getNomCommercial())
                .adresse(g.getAdresse())
                .autre(g.getAutre())
                .situationFiscale(g.getSituationFiscale())
                .actif(g.isActif())
                .chefDeFileId(chef != null ? chef.getId() : null)
                .chefDeFileRaisonSociale(chef != null ? chef.getRaisonSociale() : null)
                .membreIds(membreIds)
                .membres(membres)
                .nifAffiche(chef != null ? chef.getNif() : null)
                .dateCreation(g.getDateCreation())
                .dateModification(g.getDateModification())
                .build();
    }

    private EntrepriseDto toEntrepriseLite(Entreprise e) {
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nomCommercial(e.getNomCommercial())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .entrepriseEtrangere(e.isEntrepriseEtrangere())
                .registreCommerceEtranger(e.getRegistreCommerceEtranger())
                .build();
    }
}
