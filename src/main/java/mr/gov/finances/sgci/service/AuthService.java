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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Utilisateur u = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Identifiants incorrects"));
        if (Boolean.FALSE.equals(u.getActif())) {
            throw new BadCredentialsException("Compte en attente de validation par un administrateur");
        }
        if (!passwordEncoder.matches(request.getPassword(), u.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants incorrects");
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
