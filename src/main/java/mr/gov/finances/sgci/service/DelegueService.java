package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateDelegueRequest;
import mr.gov.finances.sgci.web.dto.UpdateDelegueRequest;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DelegueService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final List<Role> DELEGUE_ROLES = List.of(Role.AUTORITE_UPM, Role.AUTORITE_UEP);

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findMyDelegues(AuthenticatedUser user) {
        AutoriteContractante ac = requireAutoriteContractante(user);
        return utilisateurRepository.findByAutoriteContractanteIdAndRoleIn(ac.getId(), DELEGUE_ROLES)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UtilisateurDto findById(Long delegueId, AuthenticatedUser user) {
        AutoriteContractante ac = requireAutoriteContractante(user);
        Utilisateur delegue = utilisateurRepository.findById(delegueId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + delegueId));
        if (delegue.getAutoriteContractante() == null || !delegue.getAutoriteContractante().getId().equals(ac.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: ce délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() == null || !DELEGUE_ROLES.contains(delegue.getRole())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "L'utilisateur ciblé n'est pas un délégué");
        }
        return toDto(delegue);
    }

    @Transactional
    public UtilisateurDto updateDelegue(Long delegueId, UpdateDelegueRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête vide");
        }
        boolean hasChange = (request.getNomComplet() != null && !request.getNomComplet().isBlank())
                || (request.getEmail() != null && !request.getEmail().isBlank())
                || (request.getNewPassword() != null && !request.getNewPassword().isBlank());
        if (!hasChange) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucun champ à mettre à jour");
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()
                && request.getNewPassword().length() < 8) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le mot de passe doit contenir au moins 8 caractères");
        }

        AutoriteContractante ac = requireAutoriteContractante(user);
        Utilisateur delegue = utilisateurRepository.findById(delegueId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + delegueId));
        if (delegue.getAutoriteContractante() == null || !delegue.getAutoriteContractante().getId().equals(ac.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: ce délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() == null || !DELEGUE_ROLES.contains(delegue.getRole())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "L'utilisateur ciblé n'est pas un délégué");
        }

        if (request.getNomComplet() != null && !request.getNomComplet().isBlank()) {
            delegue.setNomComplet(request.getNomComplet().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim();
            if (utilisateurRepository.existsByEmailAndIdNot(email, delegueId)) {
                throw ApiException.conflict(ApiErrorCode.CONFLICT, "Cet e-mail est déjà utilisé");
            }
            delegue.setEmail(email);
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            delegue.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        delegue = utilisateurRepository.save(delegue);
        UtilisateurDto result = toDto(delegue);
        auditService.log(AuditAction.UPDATE, "Delegue", String.valueOf(delegueId), result);
        return result;
    }

    @Transactional
    public UtilisateurDto createDelegue(CreateDelegueRequest request, AuthenticatedUser user) {
        AutoriteContractante ac = requireAutoriteContractante(user);

        if (request.getRole() == null || !DELEGUE_ROLES.contains(request.getRole())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Rôle délégué invalide (attendu AUTORITE_UPM ou AUTORITE_UEP)");
        }
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Ce nom d'utilisateur est déjà utilisé");
        }

        Utilisateur u = Utilisateur.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .autoriteContractante(ac)
                .nomComplet(request.getNomComplet())
                .email(request.getEmail())
                .actif(true)
                .build();

        u = utilisateurRepository.save(u);
        UtilisateurDto result = toDto(u);
        auditService.log(AuditAction.CREATE, "Delegue", String.valueOf(u.getId()), result);
        return result;
    }

    @Transactional
    public void setDelegueActif(Long delegueId, boolean actif, AuthenticatedUser user) {
        AutoriteContractante ac = requireAutoriteContractante(user);

        Utilisateur delegue = utilisateurRepository.findById(delegueId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + delegueId));

        if (delegue.getAutoriteContractante() == null || !delegue.getAutoriteContractante().getId().equals(ac.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: ce délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() == null || !DELEGUE_ROLES.contains(delegue.getRole())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "L'utilisateur ciblé n'est pas un délégué");
        }

        delegue.setActif(actif);
        utilisateurRepository.save(delegue);
        auditService.log(AuditAction.UPDATE, "Delegue", String.valueOf(delegueId),
                java.util.Map.of("actif", actif));
    }

    private AutoriteContractante requireAutoriteContractante(AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.isImpersonating()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED,
                    "Gestion des délégués interdite pendant une session d’impersonation");
        }
        if (user.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Action réservée à l'autorité contractante");
        }
        Utilisateur entity = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (entity.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }
        return entity.getAutoriteContractante();
    }

    private UtilisateurDto toDto(Utilisateur u) {
        return UtilisateurDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .email(u.getEmail())
                .actif(u.getActif())
                .build();
    }
}
