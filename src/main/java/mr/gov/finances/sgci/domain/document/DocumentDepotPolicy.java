package mr.gov.finances.sgci.domain.document;

import java.util.Set;

/**
 * Qui dépose quoi dans une demande de correction d'offre fiscale.
 *
 * <p>Le processus {@code CORRECTION_OFFRE_FISCALE} mêle deux familles de pièces : celles que le
 * demandeur apporte à l'appui de sa demande, et celles que la Commission produit en la traitant —
 * offre corrigée, lettre d'adoption, crédits arrêtés, feuille d'évaluation signée. Rien ne les
 * distinguait : {@code DocumentRequirement} ne porte ni phase ni déposant, et le formulaire de dépôt
 * réclamait donc au demandeur des documents qu'il ne peut pas avoir, puisqu'ils n'existent pas encore.
 *
 * <p>La règle vit en code, comme {@link DocumentVisibilitePolicy} : en donnée, elle deviendrait
 * modifiable en exploitation et un code non classé rouvrirait le défaut à chaque nouveau paramétrage.
 *
 * <p>Le garde-fou est le second ensemble. Un seul ensemble souffrirait de l'oubli — un code absent de
 * la liste des pièces produites redeviendrait proposé au dépôt, sans que rien ne le signale. Avec les
 * deux, {@code DocumentCorrectionDepotIT} échoue dès qu'un code paramétré n'est classé nulle part :
 * « oublié ⇒ proposé à tort » devient « oublié ⇒ test rouge ».
 */
public final class DocumentDepotPolicy {

    /** Apportées par le demandeur à l'appui de sa demande. */
    public static final Set<String> CODES_DEPOSES_PAR_LE_DEMANDEUR = Set.of(
            "LETTRE_SAISINE",
            "PV_OUVERTURE",
            "ATTESTATION_FISCALE",
            "OFFRE_FISCALE",
            "OFFRE_FINANCIERE",
            "TABLEAU_MODELE",
            "DAO_DQE",
            "LISTE_ITEMS");

    /** Produites par la Commission pendant l'instruction, donc jamais réclamées au dépôt. */
    public static final Set<String> CODES_PRODUITS_PAR_LA_COMMISSION = Set.of(
            "FEUILLE_EVALUATION_SIGNEE",
            "OFFRE_FISCALE_CORRIGEE",
            "OFFRE_CORRIGEE",
            "CREDIT_EXTERIEUR",
            "CREDIT_INTERIEUR",
            "LETTRE_ADOPTION",
            "RECU_DEPOT");

    private DocumentDepotPolicy() {
    }

    /**
     * Vrai si ce code doit être proposé au demandeur au moment du dépôt.
     *
     * <p>Un code inconnu est traité comme déposable : le paramétrage reste ouvert à l'ADMIN_SI, et un
     * type ajouté en exploitation ne doit pas disparaître silencieusement du formulaire. C'est le test
     * de couverture, et non ce défaut, qui protège les codes connus.
     */
    public static boolean deposableParLeDemandeur(String codeDocument) {
        return codeDocument != null && !CODES_PRODUITS_PAR_LA_COMMISSION.contains(codeDocument);
    }
}
