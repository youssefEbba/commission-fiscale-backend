package mr.gov.finances.sgci.security;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;

import org.springframework.stereotype.Service;

/**
 * Résout l’entreprise / l’autorité « vus » par l’API quand un compte commission relais utilise un JWT d’impersonation
 * ({@link AuthenticatedUser#isImpersonating()}).
 */
@Service
@RequiredArgsConstructor
public class EffectiveIdentityService {

    private final AutoriteContractanteRepository autoriteContractanteRepository;

    public Long resolveEntrepriseId(AuthenticatedUser auth, Utilisateur dbUser) {
        if (auth != null && auth.isImpersonating() && auth.getActingEntrepriseId() != null) {
            return auth.getActingEntrepriseId();
        }
        return dbUser != null && dbUser.getEntreprise() != null ? dbUser.getEntreprise().getId() : null;
    }

    public Long resolveAutoriteContractanteId(AuthenticatedUser auth, Utilisateur dbUser) {
        if (auth != null && auth.isImpersonating() && auth.getActingAutoriteContractanteId() != null) {
            return auth.getActingAutoriteContractanteId();
        }
        return dbUser != null && dbUser.getAutoriteContractante() != null
                ? dbUser.getAutoriteContractante().getId()
                : null;
    }

    public AutoriteContractante requireEffectiveAutorite(AuthenticatedUser auth, Utilisateur dbUser) {
        Long id = resolveAutoriteContractanteId(auth, dbUser);
        if (id == null) {
            return null;
        }
        return autoriteContractanteRepository.findById(id).orElse(null);
    }

    /** Rôle métier effectif pour les branches métier : porté par le JWT (impersonation incluse). */
    public Role effectiveRole(AuthenticatedUser auth) {
        return auth != null ? auth.getRole() : null;
    }

    public boolean isEffectiveEntreprise(AuthenticatedUser auth) {
        return auth != null && auth.getRole() == Role.ENTREPRISE;
    }

    public boolean isEffectiveSousTraitant(AuthenticatedUser auth) {
        return auth != null && auth.getRole() == Role.SOUS_TRAITANT;
    }

    public boolean isEffectiveAutoriteContractante(AuthenticatedUser auth) {
        return auth != null && (auth.getRole() == Role.AUTORITE_CONTRACTANTE
                || auth.getRole() == Role.AUTORITE_UPM
                || auth.getRole() == Role.AUTORITE_UEP);
    }
}
