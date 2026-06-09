package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.notification.WorkflowNotificationContext;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkflowRecipientResolver {

    private final UtilisateurRepository utilisateurRepository;

    public List<Long> resolve(WorkflowNotificationContext ctx) {
        Set<Long> ids = new LinkedHashSet<>();
        if (ctx.getExplicitUserIds() != null) {
            ctx.getExplicitUserIds().stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ctx.getEntrepriseId() != null) {
            ids.addAll(activeUserIdsForEntreprise(ctx.getEntrepriseId()));
        }
        if (ctx.getAutoriteContractanteId() != null) {
            ids.addAll(activeUserIdsForAutorite(ctx.getAutoriteContractanteId()));
        }
        if (ctx.getRoleRecipients() != null) {
            for (Role role : ctx.getRoleRecipients()) {
                ids.addAll(activeUserIdsForRole(role));
            }
        }
        if (ctx.getActor() != null && ctx.getActor().getUserId() != null) {
            ids.remove(ctx.getActor().getUserId());
        }
        return new ArrayList<>(ids);
    }

    public List<Long> activeUserIdsForEntreprise(Long entrepriseId) {
        if (entrepriseId == null) {
            return List.of();
        }
        return utilisateurRepository.findByEntrepriseId(entrepriseId).stream()
                .filter(this::isActive)
                .map(Utilisateur::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Long> activeUserIdsForAutorite(Long autoriteId) {
        if (autoriteId == null) {
            return List.of();
        }
        return utilisateurRepository.findByAutoriteContractanteId(autoriteId).stream()
                .filter(this::isActive)
                .map(Utilisateur::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Long> activeUserIdsForRole(Role role) {
        if (role == null) {
            return List.of();
        }
        return utilisateurRepository.findByRoleAndActifTrue(role).stream()
                .map(Utilisateur::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Long> activeUserIdsForRoles(Role... roles) {
        if (roles == null || roles.length == 0) {
            return List.of();
        }
        return Stream.of(roles)
                .flatMap(r -> activeUserIdsForRole(r).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Long> correctionRelatedUsers(Long entrepriseId, Long autoriteId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(activeUserIdsForEntreprise(entrepriseId));
        ids.addAll(activeUserIdsForAutorite(autoriteId));
        ids.addAll(activeUserIdsForRoles(Role.PRESIDENT, Role.DGD, Role.DGTCP, Role.DGI, Role.DGB));
        return new ArrayList<>(ids);
    }

    public List<String> resolveEmails(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return utilisateurRepository.findAllById(userIds).stream()
                .map(Utilisateur::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isActive(Utilisateur u) {
        return u != null && (u.getActif() == null || Boolean.TRUE.equals(u.getActif()));
    }
}
