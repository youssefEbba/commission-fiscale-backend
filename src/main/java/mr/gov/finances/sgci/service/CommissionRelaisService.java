package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.config.JwtProperties;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.JwtService;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.ImpersonateAutoriteRequest;
import mr.gov.finances.sgci.web.dto.ImpersonateEntrepriseRequest;
import mr.gov.finances.sgci.web.dto.LoginResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionRelaisService {

    private final UtilisateurRepository utilisateurRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public Page<EntrepriseDto> listEntreprises(AuthenticatedUser auth, Pageable pageable, String q) {
        requireCommissionRelaisAccount(auth.getUserId());
        if (q == null || q.isBlank()) {
            return entrepriseRepository.findAll(pageable).map(this::toEntrepriseDto);
        }
        String qq = q.trim();
        return entrepriseRepository
                .findByRaisonSocialeContainingIgnoreCaseOrNifContainingIgnoreCase(qq, qq, pageable)
                .map(this::toEntrepriseDto);
    }

    @Transactional(readOnly = true)
    public Page<AutoriteContractanteDto> listAutoritesContractantes(AuthenticatedUser auth, Pageable pageable, String q) {
        requireCommissionRelaisAccount(auth.getUserId());
        if (q == null || q.isBlank()) {
            return autoriteContractanteRepository.findAll(pageable).map(this::toAutoriteDto);
        }
        String qq = q.trim();
        return autoriteContractanteRepository
                .findByNomContainingIgnoreCaseOrCodeContainingIgnoreCase(qq, qq, pageable)
                .map(this::toAutoriteDto);
    }

    @Transactional(readOnly = true)
    public LoginResponse impersonateEntreprise(AuthenticatedUser auth, ImpersonateEntrepriseRequest request) {
        Utilisateur relais = requireCommissionRelaisAccount(auth.getUserId());
        Entreprise ent = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise introuvable"));
        List<String> perms = new ArrayList<>(permissionService.findPermissionCodesByRole(Role.ENTREPRISE));
        String token = jwtService.generateToken(
                relais.getUsername(),
                Role.ENTREPRISE,
                relais.getId(),
                perms,
                true,
                ent.getId(),
                null,
                jwtProperties.getRelaisExpirationMs()
        );
        return buildLoginResponse(relais, Role.ENTREPRISE, perms, token, true, ent.getId(), null);
    }

    @Transactional(readOnly = true)
    public LoginResponse impersonateAutorite(AuthenticatedUser auth, ImpersonateAutoriteRequest request) {
        Utilisateur relais = requireCommissionRelaisAccount(auth.getUserId());
        AutoriteContractante ac = autoriteContractanteRepository.findById(request.getAutoriteContractanteId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante introuvable"));
        List<String> perms = new ArrayList<>(permissionService.findPermissionCodesByRole(Role.AUTORITE_CONTRACTANTE));
        String token = jwtService.generateToken(
                relais.getUsername(),
                Role.AUTORITE_CONTRACTANTE,
                relais.getId(),
                perms,
                true,
                null,
                ac.getId(),
                jwtProperties.getRelaisExpirationMs()
        );
        return buildLoginResponse(relais, Role.AUTORITE_CONTRACTANTE, perms, token, true, null, ac.getId());
    }

    @Transactional(readOnly = true)
    public LoginResponse release(AuthenticatedUser auth) {
        Utilisateur relais = requireCommissionRelaisAccount(auth.getUserId());
        List<String> perms = new ArrayList<>(permissionService.findPermissionCodesByRole(Role.COMMISSION_RELAIS));
        String token = jwtService.generateToken(
                relais.getUsername(),
                Role.COMMISSION_RELAIS,
                relais.getId(),
                perms,
                false,
                null,
                null,
                jwtProperties.getExpirationMs()
        );
        return buildLoginResponse(relais, Role.COMMISSION_RELAIS, perms, token, false, null, null);
    }

    private Utilisateur requireCommissionRelaisAccount(Long userId) {
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Utilisateur introuvable"));
        if (u.getRole() != Role.COMMISSION_RELAIS) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Réservé aux comptes commission relais");
        }
        return u;
    }

    private LoginResponse buildLoginResponse(Utilisateur relais, Role effectiveRole, List<String> permissions,
                                            String token, boolean impersonating,
                                            Long actingEntrepriseId, Long actingAutoriteContractanteId) {
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(relais.getId())
                .username(relais.getUsername())
                .role(effectiveRole)
                .nomComplet(relais.getNomComplet())
                .entrepriseId(actingEntrepriseId != null ? actingEntrepriseId
                        : (relais.getEntreprise() != null ? relais.getEntreprise().getId() : null))
                .autoriteContractanteId(actingAutoriteContractanteId != null ? actingAutoriteContractanteId
                        : (relais.getAutoriteContractante() != null ? relais.getAutoriteContractante().getId() : null))
                .permissions(permissions)
                .impersonating(impersonating)
                .actingEntrepriseId(actingEntrepriseId)
                .actingAutoriteContractanteId(actingAutoriteContractanteId)
                .build();
    }

    private EntrepriseDto toEntrepriseDto(Entreprise e) {
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .situationFiscale(e.getSituationFiscale())
                .build();
    }

    private AutoriteContractanteDto toAutoriteDto(AutoriteContractante a) {
        return AutoriteContractanteDto.builder()
                .id(a.getId())
                .nom(a.getNom())
                .code(a.getCode())
                .contact(a.getContact())
                .build();
    }
}
