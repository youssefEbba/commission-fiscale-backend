package mr.gov.finances.sgci.domain.enums;

/**
 * Les indicateurs dont la valeur d'avant la digitalisation est saisie par l'administrateur.
 *
 * <p>L'archive reprise donne les volumes et les montants d'avant, mais aucune date de décision :
 * les délais et les taux d'avant ne peuvent venir que des registres papier de la Commission. Chaque
 * code correspond à un indicateur calculé après la bascule, pour que la comparaison soit terme à
 * terme.
 */
public enum IndicateurReference {

    /** Délai moyen, en jours, du dépôt à la décision sur une demande de correction. */
    DELAI_MOYEN_CORRECTION_JOURS,

    /** Délai moyen, en jours, du dépôt à l'ouverture du crédit (mise en place). */
    DELAI_MOYEN_MISE_EN_PLACE_JOURS,

    /** Délai moyen, en jours, du dépôt au débit du crédit (utilisation). */
    DELAI_MOYEN_UTILISATION_JOURS,

    /** Part des demandes de correction adoptées parmi celles tranchées, en %. */
    TAUX_VALIDATION_CORRECTION_PCT,

    /** Part des demandes de correction rejetées parmi celles tranchées, en %. */
    TAUX_REJET_CORRECTION_PCT,

    /** Part des dossiers ayant fait l'objet d'au moins une demande de compléments, en %. */
    TAUX_COMPLEMENTS_PCT,

    /** Nombre de demandes de crédit d'impôt traitées par an. */
    DEMANDES_TRAITEES_PAR_AN
}
