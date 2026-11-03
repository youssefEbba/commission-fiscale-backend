package mr.gov.finances.sgci.domain.enums;

/**
 * Taille de l'entreprise bénéficiaire, saisie par l'administration (cahier des charges, point 4).
 * Les entreprises existantes restent {@link #NON_RENSEIGNEE} jusqu'à ce qu'on les complète : les
 * tableaux de bord affichent cette ligne plutôt que de les ranger d'office dans une catégorie.
 */
public enum CategorieEntreprise {
    PME,
    GRANDE_ENTREPRISE,
    NON_RENSEIGNEE
}
