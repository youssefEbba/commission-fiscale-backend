package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateDelegueRequest;
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

    @Transactional
    public UtilisateurDto createDelegue(CreateDelegueRequest request, AuthenticatedUser user) {
        AutoriteContractante ac = requireAutoriteContractante(user);

        if (request.getRole() == null || !DELEGUE_ROLES.contains(request.getRole())) {
            throw new RuntimeException("Rôle délégué invalide (attendu AUTORITE_UPM ou AUTORITE_UEP)");
        }
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Ce nom d'utilisateur est déjà utilisé");
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
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + delegueId));

        if (delegue.getAutoriteContractante() == null || !delegue.getAutoriteContractante().getId().equals(ac.getId())) {
            throw new RuntimeException("Accès refusé: ce délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() == null || !DELEGUE_ROLES.contains(delegue.getRole())) {
            throw new RuntimeException("L'utilisateur ciblé n'est pas un délégué");
        }

        delegue.setActif(actif);
        utilisateurRepository.save(delegue);
        auditService.log(AuditAction.UPDATE, "Delegue", String.valueOf(delegueId),
                java.util.Map.of("actif", actif));
    }

    private AutoriteContractante requireAutoriteContractante(AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw new RuntimeException("Action réservée à l'autorité contractante");
        }
        Utilisateur entity = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (entity.getAutoriteContractante() == null) {
            throw new RuntimeException("Aucune autorité contractante liée à l'utilisateur");
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
