package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.JwtService;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import mr.gov.finances.sgci.web.dto.LoginResponse;
import mr.gov.finances.sgci.web.dto.RegisterRequest;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

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
        String token = jwtService.generateToken(u.getUsername(), u.getRole(), u.getId());
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .build();
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Ce nom d'utilisateur est déjà utilisé");
        }
        Utilisateur u = Utilisateur.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .nomComplet(request.getNomComplet())
                .email(request.getEmail())
                // Nouveau compte créé inactif : l'administrateur doit le valider
                .actif(false)
                .build();
        u = utilisateurRepository.save(u);
        auditService.log(AuditAction.CREATE, "Utilisateur", String.valueOf(u.getId()),
                Map.of("username", u.getUsername(), "role", u.getRole().name(), "nomComplet", u.getNomComplet() != null ? u.getNomComplet() : ""));
        String token = jwtService.generateToken(u.getUsername(), u.getRole(), u.getId());
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .build();
    }
}
