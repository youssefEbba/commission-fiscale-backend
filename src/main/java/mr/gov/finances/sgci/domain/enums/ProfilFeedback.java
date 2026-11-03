package mr.gov.finances.sgci.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Les profils d'usagers dont on recueille l'avis (cahier des charges, point 3), et les rôles qui
 * les composent. L'administrateur SI n'en fait pas partie : il exploite la plateforme, il n'en est
 * pas l'usager.
 */
public enum ProfilFeedback {

    ENTREPRISE(EnumSet.of(Role.ENTREPRISE, Role.SOUS_TRAITANT)),
    COMMISSION_FISCALE(EnumSet.of(Role.PRESIDENT, Role.DGI, Role.DGB)),
    AUTORITE_CONTRACTANTE(EnumSet.of(Role.AUTORITE_CONTRACTANTE, Role.AUTORITE_UPM, Role.AUTORITE_UEP)),
    DOUANES(EnumSet.of(Role.DGD)),
    TRESOR(EnumSet.of(Role.DGTCP));

    private final Set<Role> roles;

    ProfilFeedback(Set<Role> roles) {
        this.roles = roles;
    }

    public Set<Role> roles() {
        return roles;
    }

    /** Le profil d'un rôle ; {@code null} pour un rôle qui ne donne pas d'avis (ADMIN_SI, relais). */
    public static ProfilFeedback deRole(Role role) {
        for (ProfilFeedback p : values()) {
            if (p.roles.contains(role)) {
                return p;
            }
        }
        return null;
    }
}
