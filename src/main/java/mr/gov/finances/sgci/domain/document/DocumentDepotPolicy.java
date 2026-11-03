package mr.gov.finances.sgci.domain.document;

import mr.gov.finances.sgci.domain.enums.ProcessusDocument;

import java.util.Set;

/**
 * Qui dépose quoi, processus par processus : ce que le formulaire de dépôt propose au demandeur.
 *
 * <p>Deux processus mêlent deux familles de pièces : celles que le demandeur apporte à l'appui de
 * sa demande, et celles qui naissent pendant le traitement. Dans la demande de correction, ce sont
 * l'offre corrigée, la lettre d'adoption, les crédits arrêtés, la feuille d'évaluation signée. Dans
 * l'utilisation, ce sont le certificat d'utilisation, le bulletin annoté, les quittances — et le
 * chèque certifié, que l'entreprise fournit bien, mais à l'étape du chèque, pas au dépôt. Rien ne
 * les distinguait : {@code DocumentRequirement} ne porte ni phase ni déposant, et le formulaire de
 * dépôt réclamait donc au demandeur des documents qu'il ne peut pas avoir, puisqu'ils n'existent pas
 * encore.
 *
 * <p>La règle vit en code, comme {@code VisibiliteDocumentService} : en donnée, elle deviendrait
 * modifiable en exploitation et un code non classé rouvrirait le défaut à chaque nouveau paramétrage.
 *
 * <p>Le garde-fou est le second ensemble de chaque processus. Un seul ensemble souffrirait de
 * l'oubli — un code absent de la liste des pièces hors dépôt redeviendrait proposé, sans que rien ne
 * le signale. Avec les deux, {@code DocumentCorrectionDepotIT} et {@code PiecesDepotUtilisationIT}
 * échouent dès qu'un code paramétré n'est classé nulle part : « oublié ⇒ proposé à tort » devient
 * « oublié ⇒ test rouge ».
 */
public final class DocumentDepotPolicy {

    /** Correction : apportées par le demandeur à l'appui de sa demande. */
    public static final Set<String> CODES_DEPOSES_PAR_LE_DEMANDEUR = Set.of(
            "LETTRE_SAISINE",
            "PV_OUVERTURE",
            "ATTESTATION_FISCALE",
            "OFFRE_FISCALE",
            "OFFRE_FINANCIERE",
            "TABLEAU_MODELE",
            "DAO_DQE",
            "LISTE_ITEMS",
            // Exigée seulement quand l'autorité contractante déclare des équipements : l'obligation
            // est conditionnelle, donc en code, et le référentiel la laisse facultative.
            "LISTE_EQUIPEMENTS");

    /** Correction : produites par la Commission pendant l'instruction, donc jamais réclamées au dépôt. */
    public static final Set<String> CODES_PRODUITS_PAR_LA_COMMISSION = Set.of(
            "FEUILLE_EVALUATION_SIGNEE",
            "OFFRE_FISCALE_CORRIGEE",
            "OFFRE_CORRIGEE",
            "CREDIT_EXTERIEUR",
            "CREDIT_INTERIEUR",
            "LETTRE_ADOPTION",
            "RECU_DEPOT");

    /** Utilisation, en douane comme en TVA intérieure : apportées par l'entreprise à l'appui de sa demande. */
    public static final Set<String> UTILISATION_CODES_DEPOSES = Set.of(
            "DEMANDE_UTILISATION",
            "ORDRE_TRANSIT",
            "DECLARATION_DOUANE",
            "BULLETIN_LIQUIDATION",
            "FACTURE",
            "CONNAISSEMENT",
            "CERTIFICAT_CREDIT_IMPOTS_SYDONIA",
            "DECLARATION_TVA",
            "DECOMPTE");

    /**
     * Utilisation : les pièces des étapes du circuit, jamais proposées au dépôt.
     *
     * <p>Toutes sont produites par les directions, sauf le chèque certifié : l'entreprise le fournit,
     * mais quand la DGD a arrêté la part à payer — il n'a pas de montant au moment du dépôt.
     */
    public static final Set<String> UTILISATION_CODES_DU_CIRCUIT = Set.of(
            "CERTIFICAT_UTILISATION",
            "BULLETIN_ANNOTE",
            "CHEQUE_CERTIFIE",
            "QUITTANCE_TRESOR",
            "QUITTANCE_DGI");

    /** Avenant : apportées par l'entreprise ou l'autorité contractante à l'appui de son avenant. */
    public static final Set<String> AVENANT_CODES_DEPOSES = Set.of(
            "NOTE_SERVICE",
            "JUSTIFICATIONS_LEGALES",
            "LETTRES_MOTIVEES",
            "AVENANT_CONTRAT",
            "LETTRES_AUTORITE_CONTRACTANTE",
            "DETAIL_CORRECTIONS_NECESSAIRES",
            "DOCUMENTS_OFFICIELS");

    /** Avenant : produite par la Commission, jointe par le Président une fois l'avenant examiné. */
    public static final Set<String> AVENANT_CODES_DE_LA_COMMISSION = Set.of(
            "DECISION_COMMISSION");

    /**
     * Mise en place : le certificat de crédit d'impôt signé est la pièce que le Président (ou
     * l'administrateur qui le supplée) dépose avant sa validation. Proposé au déposant, il laissait
     * un fichier quelconque de l'autorité contractante remplir cette condition (anomalie A43).
     */
    public static final Set<String> MISE_EN_PLACE_CODES_DE_LA_PRESIDENCE = Set.of(
            "CERTIFICAT_CREDIT_IMPOTS");

    private DocumentDepotPolicy() {
    }

    /**
     * Vrai si ce code doit être proposé au demandeur au moment du dépôt, dans ce processus.
     *
     * <p>Un code inconnu est traité comme déposable : le paramétrage reste ouvert à l'ADMIN_SI, et un
     * type ajouté en exploitation ne doit pas disparaître silencieusement du formulaire. C'est le test
     * de couverture, et non ce défaut, qui protège les codes connus. Pour la même raison, un processus
     * sans famille hors dépôt propose toutes ses pièces.
     */
    public static boolean deposableParLeDemandeur(ProcessusDocument processus, String codeDocument) {
        return codeDocument != null && !horsDepot(processus).contains(codeDocument);
    }

    /** Les pièces de ce processus que le formulaire de dépôt ne propose jamais. */
    public static Set<String> horsDepot(ProcessusDocument processus) {
        if (processus == null) {
            return Set.of();
        }
        return switch (processus) {
            case CORRECTION_OFFRE_FISCALE -> CODES_PRODUITS_PAR_LA_COMMISSION;
            case UTILISATION_CI, UTILISATION_CI_DOUANE, UTILISATION_CI_TVA_INTERIEURE,
                 UTILISATION_CI_EXTERIEUR, UTILISATION_CI_INTERIEUR -> UTILISATION_CODES_DU_CIRCUIT;
            case MODIFICATION_CI -> AVENANT_CODES_DE_LA_COMMISSION;
            case MISE_EN_PLACE_CI -> MISE_EN_PLACE_CODES_DE_LA_PRESIDENCE;
            default -> Set.of();
        };
    }

    /** Les pièces de ce processus que le demandeur apporte au dépôt ; vide si le processus n'est pas classé. */
    public static Set<String> deposees(ProcessusDocument processus) {
        if (processus == null) {
            return Set.of();
        }
        return switch (processus) {
            case CORRECTION_OFFRE_FISCALE -> CODES_DEPOSES_PAR_LE_DEMANDEUR;
            case UTILISATION_CI, UTILISATION_CI_DOUANE, UTILISATION_CI_TVA_INTERIEURE,
                 UTILISATION_CI_EXTERIEUR, UTILISATION_CI_INTERIEUR -> UTILISATION_CODES_DEPOSES;
            case MODIFICATION_CI -> AVENANT_CODES_DEPOSES;
            default -> Set.of();
        };
    }
}
