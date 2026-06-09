package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ChangeMyPasswordRequest;
import mr.gov.finances.sgci.web.dto.SousTraitantUtilisateurDto;
import mr.gov.finances.sgci.web.dto.UpdateMyProfileRequest;
import mr.gov.finances.sgci.web.dto.UpdateUtilisateurRequest;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findAll() {
        return utilisateurRepository.findAllByOrderByIdDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UtilisateurDto findById(Long id) {
        return utilisateurRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + id));
    }

    @Transactional(readOnly = true)
    public UtilisateurDto findMyProfile(AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        return utilisateurRepository.findById(user.getUserId())
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
    }

    @Transactional(readOnly = true)
    public List<SousTraitantUtilisateurDto> findSousTraitants() {
        return utilisateurRepository.findByRole(Role.SOUS_TRAITANT)
                .stream()
                .map(u -> SousTraitantUtilisateurDto.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nomComplet(u.getNomComplet())
                        .email(u.getEmail())
                        .actif(u.getActif())
                        .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                        .entrepriseRaisonSociale(u.getEntreprise() != null ? u.getEntreprise().getRaisonSociale() : null)
                        .entrepriseNif(u.getEntreprise() != null ? u.getEntreprise().getNif() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findPending() {
        return utilisateurRepository.findByActifFalseOrderByIdDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UtilisateurDto update(Long id, UpdateUtilisateurRequest request, AuthenticatedUser actor) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête vide");
        }
        if (!hasAdminUpdateField(request)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucun champ à mettre à jour");
        }

        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + id));

        if (request.getNomComplet() != null) {
            u.setNomComplet(normalizeOptionalText(request.getNomComplet()));
        }
        applyEmailUpdate(u, request.getEmail());

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        if (request.getRole() != null && request.getRole() != u.getRole()) {
            assertCanAssignRole(actor);
            u.setRole(request.getRole());
        }

        applyOrganisationLinks(u, request);

        u = utilisateurRepository.save(u);
        UtilisateurDto result = toDto(u);
        auditService.log(AuditAction.UPDATE, "Utilisateur", String.valueOf(id), toAuditSnapshot(result));
        return result;
    }

    @Transactional
    public UtilisateurDto updateMyProfile(AuthenticatedUser user, UpdateMyProfileRequest request) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête vide");
        }
        boolean hasChange = request.getNomComplet() != null || request.getEmail() != null;
        if (!hasChange) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucun champ à mettre à jour");
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (request.getNomComplet() != null) {
            u.setNomComplet(normalizeOptionalText(request.getNomComplet()));
        }
        applyEmailUpdate(u, request.getEmail());

        u = utilisateurRepository.save(u);
        UtilisateurDto result = toDto(u);
        auditService.log(AuditAction.UPDATE, "UtilisateurProfile", String.valueOf(u.getId()), toAuditSnapshot(result));
        return result;
    }

    @Transactional
    public void changeMyPassword(AuthenticatedUser user, ChangeMyPasswordRequest request) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête vide");
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Mot de passe actuel incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le nouveau mot de passe doit être différent de l'actuel");
        }

        u.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        utilisateurRepository.save(u);
        auditService.log(AuditAction.UPDATE, "UtilisateurPassword", String.valueOf(u.getId()),
                java.util.Map.of("username", u.getUsername(), "selfService", true));
    }

    @Transactional
    public UtilisateurDto setActif(Long id, boolean actif) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + id));
        u.setActif(actif);
        u = utilisateurRepository.save(u);
        UtilisateurDto result = toDto(u);
        auditService.log(AuditAction.UPDATE, "Utilisateur", String.valueOf(id),
                java.util.Map.of("actif", actif));
        return result;
    }

    private void applyEmailUpdate(Utilisateur u, String email) {
        if (email == null) {
            return;
        }
        String normalized = normalizeOptionalText(email);
        if (normalized != null && utilisateurRepository.existsByEmailAndIdNot(normalized, u.getId())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Cet e-mail est déjà utilisé");
        }
        u.setEmail(normalized);
    }

    private void applyOrganisationLinks(Utilisateur u, UpdateUtilisateurRequest request) {
        Role effectiveRole = u.getRole();
        if (effectiveRole == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le rôle de l'utilisateur est obligatoire");
        }

        if (needsAutoriteContractante(effectiveRole)) {
            Long autoriteId = request.getAutoriteContractanteId();
            if (autoriteId == null) {
                if (u.getAutoriteContractante() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                            "Une autorité contractante est requise pour le rôle " + effectiveRole.name());
                }
                return;
            }
            AutoriteContractante autorite = autoriteContractanteRepository.findById(autoriteId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                            "Autorité contractante non trouvée: " + autoriteId));
            u.setAutoriteContractante(autorite);
            u.setEntreprise(null);
            return;
        }

        if (needsEntreprise(effectiveRole)) {
            Long entrepriseId = request.getEntrepriseId();
            if (entrepriseId == null) {
                if (u.getEntreprise() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                            "Une entreprise est requise pour le rôle " + effectiveRole.name());
                }
                return;
            }
            Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                            "Entreprise non trouvée: " + entrepriseId));
            u.setEntreprise(entreprise);
            u.setAutoriteContractante(null);
            return;
        }

        if (request.getAutoriteContractanteId() != null || request.getEntrepriseId() != null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ce rôle ne peut pas être rattaché à une autorité contractante ou à une entreprise");
        }
        u.setAutoriteContractante(null);
        u.setEntreprise(null);
    }

    private void assertCanAssignRole(AuthenticatedUser actor) {
        if (actor == null || actor.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Set<String> permissions = permissionService.findPermissionCodesByRole(actor.getRole());
        if (!permissions.contains("user.role.assign")) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Permission user.role.assign requise pour modifier le rôle");
        }
    }

    private static boolean hasAdminUpdateField(UpdateUtilisateurRequest request) {
        return (request.getNomComplet() != null)
                || (request.getEmail() != null)
                || (request.getRole() != null)
                || (request.getAutoriteContractanteId() != null)
                || (request.getEntrepriseId() != null)
                || (request.getNewPassword() != null && !request.getNewPassword().isBlank());
    }

    private static boolean needsAutoriteContractante(Role role) {
        return role == Role.AUTORITE_CONTRACTANTE
                || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP;
    }

    private static boolean needsEntreprise(Role role) {
        return role == Role.ENTREPRISE || role == Role.SOUS_TRAITANT;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UtilisateurDto toDto(Utilisateur u) {
        return UtilisateurDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .email(u.getEmail())
                .actif(u.getActif())
                .autoriteContractanteId(u.getAutoriteContractante() != null ? u.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(u.getAutoriteContractante() != null ? u.getAutoriteContractante().getNom() : null)
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                .entrepriseRaisonSociale(u.getEntreprise() != null ? u.getEntreprise().getRaisonSociale() : null)
                .build();
    }

    private static java.util.Map<String, Object> toAuditSnapshot(UtilisateurDto dto) {
        java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("id", dto.getId());
        snap.put("username", dto.getUsername());
        snap.put("role", dto.getRole() != null ? dto.getRole().name() : null);
        snap.put("nomComplet", dto.getNomComplet());
        snap.put("email", dto.getEmail());
        snap.put("actif", dto.getActif());
        snap.put("autoriteContractanteId", dto.getAutoriteContractanteId());
        snap.put("entrepriseId", dto.getEntrepriseId());
        return snap;
    }
}
