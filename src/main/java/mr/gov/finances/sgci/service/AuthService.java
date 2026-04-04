package mr.gov.finances.sgci.service;

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
import mr.gov.finances.sgci.web.dto.RegisterRequest;

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
        String token = jwtService.generateToken(
                u.getUsername(),
                u.getRole(),
                u.getId(),
                permissionService.findPermissionCodesByRole(u.getRole())
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
                .permissions(permissionService.findPermissionCodesByRole(u.getRole()).stream().toList())
                .build();
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Ce nom d'utilisateur est déjà utilisé");
        }
        AutoriteContractante autorite = null;
        if (request.getAutoriteContractanteId() != null) {
            autorite = autoriteContractanteRepository.findById(request.getAutoriteContractanteId())
                    .orElseThrow(() -> new RuntimeException("Autorité contractante non trouvée"));
        }
        Entreprise entreprise = null;
        if (request.getEntrepriseId() != null) {
            entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                    .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        } else if (request.getEntrepriseRaisonSociale() != null) {
            entreprise = Entreprise.builder()
                    .raisonSociale(request.getEntrepriseRaisonSociale())
                    .nif(request.getEntrepriseNif())
                    .adresse(request.getEntrepriseAdresse())
                    .situationFiscale(request.getEntrepriseSituationFiscale())
                    .build();
            entreprise = entrepriseRepository.save(entreprise);
        }

        if (autorite == null && needsAutoriteContractante(request.getRole())) {
            if (request.getAcNom() != null && !request.getAcNom().isBlank()) {
                String code = resolveNewAutoriteCode(request);
                String contact = buildAutoriteContactFromRegistration(request);
                autorite = autoriteContractanteRepository.save(AutoriteContractante.builder()
                        .nom(request.getAcNom().trim())
                        .code(code)
                        .contact(contact)
                        .build());
            } else {
                throw new RuntimeException(
                        "Pour un compte Autorité contractante, indiquez soit une autorité existante (autoriteContractanteId), "
                                + "soit le nom de votre autorité (formulaire d'inscription).");
            }
        }

        Utilisateur u = Utilisateur.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .autoriteContractante(autorite)
                .entreprise(entreprise)
                .nomComplet(request.getNomComplet())
                .email(request.getEmail())
                // Nouveau compte créé inactif : l'administrateur doit le valider
                .actif(false)
                .build();
        u = utilisateurRepository.save(u);
        auditService.log(AuditAction.CREATE, "Utilisateur", String.valueOf(u.getId()),
                Map.of("username", u.getUsername(), "role", u.getRole().name(), "nomComplet", u.getNomComplet() != null ? u.getNomComplet() : ""));
        String token = jwtService.generateToken(
                u.getUsername(),
                u.getRole(),
                u.getId(),
                permissionService.findPermissionCodesByRole(u.getRole())
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
                .permissions(permissionService.findPermissionCodesByRole(u.getRole()).stream().toList())
                .build();
    }

    private static boolean needsAutoriteContractante(Role role) {
        return role == Role.AUTORITE_CONTRACTANTE
                || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP;
    }

    private String resolveNewAutoriteCode(RegisterRequest request) {
        if (request.getAcSigle() != null && !request.getAcSigle().isBlank()) {
            String c = request.getAcSigle().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            if (!c.isEmpty() && !autoriteContractanteRepository.existsByCode(c)) {
                return c;
            }
        }
        String base = "AC_" + request.getUsername().replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        String code = base;
        int i = 0;
        while (autoriteContractanteRepository.existsByCode(code)) {
            code = base + "_" + (++i);
        }
        return code;
    }

    private static String buildAutoriteContactFromRegistration(RegisterRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getAcAdresse() != null && !request.getAcAdresse().isBlank()) {
            parts.add(request.getAcAdresse().trim());
        }
        if (request.getAcTelephone() != null && !request.getAcTelephone().isBlank()) {
            parts.add("Tél: " + request.getAcTelephone().trim());
        }
        if (request.getAcEmail() != null && !request.getAcEmail().isBlank()) {
            parts.add(request.getAcEmail().trim());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }
}
