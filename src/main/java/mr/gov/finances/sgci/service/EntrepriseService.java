package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntrepriseService {

    private final EntrepriseRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final EffectiveIdentityService effectiveIdentityService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<EntrepriseDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EntrepriseDto findById(Long id, AuthenticatedUser user) {
        assertCanViewEntreprise(id, user);
        return repository.findById(id).map(this::toDto).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée: " + id));
    }

    @Transactional(readOnly = true)
    public EntrepriseDto findMyEntreprise(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Long entrepriseId = effectiveIdentityService.resolveEntrepriseId(
                user,
                utilisateurRepository.findById(user.getUserId()).orElse(null)
        );
        if (entrepriseId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        return findById(entrepriseId, user);
    }

    @Transactional
    public EntrepriseDto create(EntrepriseDto dto) {
        assertIdentificationCoherente(dto);
        if (dto.getNif() != null && !dto.getNif().isBlank() && repository.existsByNif(dto.getNif())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Une entreprise avec ce NIF existe déjà");
        }
        Entreprise entity = Entreprise.builder()
                .raisonSociale(dto.getRaisonSociale())
                .nomCommercial(dto.getNomCommercial())
                .activite(dto.getActivite())
                .autre(dto.getAutre())
                .nif(dto.getNif())
                .adresse(dto.getAdresse())
                .situationFiscale(dto.getSituationFiscale())
                .entrepriseEtrangere(dto.isEntrepriseEtrangere())
                .registreCommerceEtranger(dto.getRegistreCommerceEtranger())
                .build();
        entity = repository.save(entity);
        EntrepriseDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Entreprise", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public EntrepriseDto update(Long id, EntrepriseDto dto) {
        Entreprise entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée: " + id));
        assertIdentificationCoherente(dto);
        entity.setRaisonSociale(dto.getRaisonSociale());
        entity.setNomCommercial(dto.getNomCommercial());
        entity.setActivite(dto.getActivite());
        entity.setAutre(dto.getAutre());
        entity.setNif(dto.getNif());
        entity.setAdresse(dto.getAdresse());
        entity.setSituationFiscale(dto.getSituationFiscale());
        entity.setEntrepriseEtrangere(dto.isEntrepriseEtrangere());
        entity.setRegistreCommerceEtranger(dto.getRegistreCommerceEtranger());
        entity = repository.save(entity);
        EntrepriseDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "Entreprise", String.valueOf(id), result);
        return result;
    }

    /**
     * Règles d'identification :
     * <ul>
     *   <li>entreprise étrangère → NIF facultatif, {@code registreCommerceEtranger} obligatoire ;</li>
     *   <li>sinon → NIF obligatoire.</li>
     * </ul>
     * Les groupements sont gérés via l'entité {@code Groupement} (NIF = NIF du chef de file).
     */
    private void assertIdentificationCoherente(EntrepriseDto dto) {
        boolean nifRenseigne = dto.getNif() != null && !dto.getNif().isBlank();
        if (dto.isEntrepriseEtrangere()) {
            if (dto.getRegistreCommerceEtranger() == null || dto.getRegistreCommerceEtranger().isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le registre de commerce étranger est obligatoire pour une entreprise étrangère");
            }
            return;
        }
        if (!nifRenseigne) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le NIF est obligatoire pour une entreprise locale");
        }
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée: " + id);
        }
        auditService.log(AuditAction.DELETE, "Entreprise", String.valueOf(id), null);
        repository.deleteById(id);
    }

    private void assertCanViewEntreprise(Long entrepriseId, AuthenticatedUser user) {
        if (hasAuthority("entreprise.list")) {
            return;
        }
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Long effectiveEntrepriseId = effectiveIdentityService.resolveEntrepriseId(
                user,
                utilisateurRepository.findById(user.getUserId()).orElse(null)
        );
        if (effectiveEntrepriseId != null && effectiveEntrepriseId.equals(entrepriseId)) {
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: entreprise hors périmètre");
    }

    private static boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private EntrepriseDto toDto(Entreprise e) {
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nomCommercial(e.getNomCommercial())
                .activite(e.getActivite())
                .autre(e.getAutre())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .situationFiscale(e.getSituationFiscale())
                .entrepriseEtrangere(e.isEntrepriseEtrangere())
                .registreCommerceEtranger(e.getRegistreCommerceEtranger())
                .build();
    }
}
