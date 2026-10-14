package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/**
 * Détermine ce qu'un utilisateur a le droit de voir dans le reporting.
 *
 * <p>Point unique : tous les blocs passent par ici. Un bloc qui recalculerait son périmètre
 * autrement finirait par diverger de celui-ci, et la divergence ne se verrait qu'en recette.
 */
@Component
@RequiredArgsConstructor
public class ReportingScopeFactory {

    /** Les rôles qui voient l'ensemble du territoire, et sont donc seuls à pouvoir filtrer. */
    private static final Set<Role> ROLES_NATIONAUX = EnumSet.of(
            Role.PRESIDENT, Role.ADMIN_SI, Role.DGD, Role.DGTCP, Role.DGI, Role.DGB);

    /** Fenêtre par défaut quand l'appelant n'en donne pas. */
    private static final int FENETRE_DEFAUT_JOURS = 365;

    private final UtilisateurRepository utilisateurRepository;
    private final EffectiveIdentityService effectiveIdentityService;

    public ReportingScope build(AuthenticatedUser auth, Instant from, Instant to,
                                Long reqAc, Long reqEnt) {
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));

        Instant t0 = to != null ? to : Instant.now();
        Instant f0 = from != null ? from : t0.minus(FENETRE_DEFAUT_JOURS, ChronoUnit.DAYS);
        Role role = auth.getRole();
        boolean national = ROLES_NATIONAUX.contains(role);

        Long ac = null;
        Long ent = null;
        Long delegueId = null;
        boolean filtersApplied = false;
        boolean perimetreVide = false;

        if (national) {
            ac = reqAc;
            ent = reqEnt;
            filtersApplied = reqAc != null || reqEnt != null;
        } else if (role == Role.ENTREPRISE || role == Role.SOUS_TRAITANT) {
            ent = effectiveIdentityService.resolveEntrepriseId(auth, u);
            perimetreVide = ent == null;
        } else if (role == Role.AUTORITE_CONTRACTANTE || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP) {
            ac = effectiveIdentityService.resolveAutoriteContractanteId(auth, u);
            if (ac == null) {
                throw new IllegalStateException("Autorité contractante requise pour ce rôle");
            }
            if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
                delegueId = u.getId();
            }
        } else {
            // Fail-closed. Cette branche recevait auparavant « aucun filtre », ce qui revenait à
            // servir les totaux nationaux : c'est exactement ce que faisait COMMISSION_RELAIS, le
            // seul rôle à qui reporting.view est refusée. Un périmètre fermé est en outre un
            // comportement défini pour tout rôle ajouté plus tard à l'énumération, là où une
            // exception transformerait cet ajout en panne.
            ent = effectiveIdentityService.resolveEntrepriseId(auth, u);
            ac = effectiveIdentityService.resolveAutoriteContractanteId(auth, u);
            perimetreVide = ent == null && ac == null;
        }

        return new ReportingScope(f0, t0, ac, ent, delegueId, national, filtersApplied,
                national, perimetreVide);
    }
}
