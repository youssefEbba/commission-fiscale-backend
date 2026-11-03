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
import mr.gov.finances.sgci.security.JwtService;
import mr.gov.finances.sgci.security.TentativesConnexion;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import mr.gov.finances.sgci.web.dto.LoginResponse;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final TentativesConnexion tentativesConnexion;

    /** Ferme toutes les sessions du compte : les jetons déjà émis sont refusés. */
    @Transactional
    public void deconnecter(Long userId) {
        if (userId == null) {
            return;
        }
        utilisateurRepository.findById(userId).ifPresent(u -> {
            u.revoquerSessions();
            utilisateurRepository.save(u);
        });
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        long blocage = tentativesConnexion.minutesDeBlocage(request.getUsername());
        if (blocage > 0) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS.value(), ApiErrorCode.TROP_DE_REQUETES,
                    "Trop de tentatives de connexion : réessayez dans " + blocage + " minute(s)");
        }
        // Le mot de passe d'abord : tout autre message (compte en attente…) dirait à n'importe qui
        // que l'identifiant existe.
        Utilisateur u = utilisateurRepository.findByUsername(request.getUsername()).orElse(null);
        if (u == null || u.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), u.getPasswordHash())) {
            tentativesConnexion.echec(request.getUsername());
            throw new BadCredentialsException("Identifiants incorrects");
        }
        tentativesConnexion.succes(request.getUsername());
        if (Boolean.FALSE.equals(u.getActif())) {
            throw new BadCredentialsException("Compte en attente de validation par un administrateur");
        }
        var permissions = permissionService.findPermissionCodesByRole(u.getRole()).stream().toList();
        String token = jwtService.generateToken(
                u.getUsername(),
                u.getRole(),
                u.getId(),
                permissionService.findPermissionCodesForJwt(u.getRole())
        );
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .autoriteContractanteId(u.getAutoriteContractante() != null ? u.getAutoriteContractante().getId() : null)
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                .permissions(permissions)
                .impersonating(false)
                .build();
    }

}
