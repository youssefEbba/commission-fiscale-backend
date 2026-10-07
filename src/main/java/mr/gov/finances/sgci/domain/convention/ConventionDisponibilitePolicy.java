package mr.gov.finances.sgci.domain.convention;

import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.enums.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Une convention désactivée n'accueille plus rien et disparaît de la vue des autorités contractantes.
 *
 * <p>La disponibilité est un axe distinct du {@code statut}, qui porte la validation : une convention
 * peut être validée et néanmoins fermée aux nouveaux dossiers, par exemple quand le financement est
 * suspendu. Les confondre aurait obligé à inventer un statut « validée mais fermée » et à le faire
 * traverser toute la machine à états.
 *
 * <p>Désactivée, la convention est masquée aux rôles d'autorité contractante et refuse tout nouveau
 * rattachement — marché comme demande de correction. Les dossiers déjà rattachés continuent de vivre
 * et d'afficher la référence de leur convention : fermer une convention ne doit pas geler une
 * instruction en cours. Les agents de la Commission continuent de la voir, sans quoi personne ne
 * pourrait plus la rouvrir.
 *
 * <p>Politique sans état, en code plutôt qu'en donnée, sur le modèle de
 * {@code DocumentVisibilitePolicy} : la règle ne doit pas pouvoir dériver par un {@code UPDATE}.
 */
public final class ConventionDisponibilitePolicy {

    /** Message unique du refus, pour que les trois points de rattachement disent la même chose. */
    public static final String MOTIF_DESACTIVEE =
            "Convention désactivée : aucun nouveau rattachement n'est possible";

    private static final Set<Role> ROLES_COMMISSION = EnumSet.of(
            Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT, Role.ADMIN_SI);

    private ConventionDisponibilitePolicy() {
    }

    /**
     * {@code null} vaut active.
     *
     * <p>La colonne est postérieure aux conventions déjà en base, et Hibernate l'y ajoute à
     * {@code null}. Lire {@code null} comme « désactivée » aurait fermé d'un coup toutes les
     * conventions existantes — l'inverse exact de l'intention.
     */
    public static boolean estActive(Convention convention) {
        return convention != null && !Boolean.FALSE.equals(convention.getActif());
    }

    /** Vrai si ce rôle continue de voir les conventions fermées. */
    public static boolean voitLesDesactivees(Role role) {
        return role != null && ROLES_COMMISSION.contains(role);
    }

    public static boolean visiblePour(Convention convention, Role role) {
        return estActive(convention) || voitLesDesactivees(role);
    }
}
