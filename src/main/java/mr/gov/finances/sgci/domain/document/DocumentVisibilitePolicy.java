package mr.gov.finances.sgci.domain.document;

import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import java.util.Set;

/**
 * Qui voit quelle pièce d'un dossier d'utilisation, et à partir de quand.
 *
 * <p>Les pièces que l'administration produit en cours d'instruction — le bulletin annoté par la DGD,
 * les quittances du Trésor et de la DGI, le certificat d'utilisation — ne sont communiquées à
 * l'entreprise qu'une fois le dossier liquidé, c'est-à-dire au dernier acte de la DGTCP. Avant cela
 * ce sont des papiers de travail ; après, ce sont les pièces du dossier.
 *
 * <p><b>Politique en code, sans état.</b> Elle n'est ni paramétrable en exploitation — une règle de
 * confidentialité qui se modifie par écran se contourne par écran — ni portée par une colonne, qui
 * serait un état à basculer, donc à dériver. La contrepartie est qu'un code oublié resterait
 * visible : d'où {@link #CODES_ENTREPRISE} et le test qui échoue si un code paramétré n'est classé
 * ni d'un côté ni de l'autre. « Oublié ⇒ visible » devient « oublié ⇒ test rouge ».
 */
public final class DocumentVisibilitePolicy {

    /** Pièces produites par l'administration, masquées à l'entreprise avant la liquidation. */
    public static final Set<String> CODES_ADMINISTRATION = Set.of(
            "BULLETIN_ANNOTE",
            "QUITTANCE_TRESOR",
            "QUITTANCE_DGI",
            "CERTIFICAT_UTILISATION");

    /**
     * Pièces déposées par l'entreprise ou pour elle : visibles en permanence.
     *
     * <p>Sert au test de couverture, pas au contrôle — c'est l'appartenance à
     * {@link #CODES_ADMINISTRATION} qui décide.
     */
    public static final Set<String> CODES_ENTREPRISE = Set.of(
            "DEMANDE_UTILISATION",
            "ORDRE_TRANSIT",
            "DECLARATION_DOUANE",
            "BULLETIN_LIQUIDATION",
            "FACTURE",
            "CONNAISSEMENT",
            "CERTIFICAT_CREDIT_IMPOTS_SYDONIA",
            "CHEQUE_CERTIFIE",
            "DECLARATION_TVA",
            "DECOMPTE");

    private DocumentVisibilitePolicy() {
    }

    public static boolean estPieceAdministration(String codeDocument) {
        return codeDocument != null && CODES_ADMINISTRATION.contains(codeDocument);
    }

    /**
     * Les statuts à partir desquels les pièces sont communiquées.
     *
     * <p>{@code CLOTUREE} y figure, et ce n'est pas un détail : sans lui les pièces se
     * re-masqueraient à l'instant où l'entreprise accuse réception. {@code REJETEE} n'ouvre rien —
     * le motif d'un rejet est porté par la décision, pas par les papiers de travail.
     */
    public static boolean statutOuvreLesPieces(StatutUtilisation statut) {
        return statut == StatutUtilisation.LIQUIDEE
                || statut == StatutUtilisation.APUREE
                || statut == StatutUtilisation.CLOTUREE;
    }

    /** Les administrations instruisent le dossier : rien ne leur est masqué. */
    public static boolean acteurVoitTout(Role role) {
        return role == Role.DGD || role == Role.DGTCP || role == Role.DGI
                || role == Role.DGB || role == Role.PRESIDENT || role == Role.ADMIN_SI;
    }

    /** {@code true} si cet acteur peut voir cette pièce sur un dossier à ce statut. */
    public static boolean visiblePour(String codeDocument, StatutUtilisation statut, Role role) {
        if (role == null) {
            return false;
        }
        if (acteurVoitTout(role)) {
            return true;
        }
        return !estPieceAdministration(codeDocument) || statutOuvreLesPieces(statut);
    }
}
