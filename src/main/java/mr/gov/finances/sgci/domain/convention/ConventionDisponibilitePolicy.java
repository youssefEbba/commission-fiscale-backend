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
 * <p>Désactivée, la convention refuse tout nouveau rattachement — marché comme demande de correction. Les dossiers déjà rattachés continuent de vivre
 * et d'afficher la référence de leur convention : fermer une convention ne doit pas geler une
 * instruction en cours.
 *
 * <p><b>Une convention fermée reste visible de qui l'administre</b>, badgée comme telle. Elle
 * disparaît des <em>sélecteurs</em> où l'on rattache un marché ou une demande, pas de la liste où on
 * la gère. La règle a été revue le jour où l'autorité contractante a reçu le droit de désactiver :
 * masquer ce qu'elle venait de fermer l'aurait empêchée de le rouvrir, pour toujours.
 *
 * <p>Politique sans état, en code plutôt qu'en donnée, sur le modèle de
 * {@code VisibiliteDocumentService} : la règle ne doit pas pouvoir dériver par un {@code UPDATE}.
 */
public final class ConventionDisponibilitePolicy {

    /** Message unique du refus, pour que les trois points de rattachement disent la même chose. */
    public static final String MOTIF_DESACTIVEE =
            "Convention désactivée : aucun nouveau rattachement n'est possible";

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

}
