package mr.gov.finances.sgci.domain.modification;

/**
 * La famille du champ visé par une modification, et non le champ lui-même.
 *
 * <p><b>Cette distinction est la correction de fond.</b> Le javadoc d'{@code adminCorrectInfo}
 * annonçait que « le récapitulatif reste corrigeable sans cette contrainte » et rangeait
 * {@code tvaImportationDouane} dans le récapitulatif. Or ce champ n'est pas un montant de
 * référence : c'est le quota TVA <em>restant</em>, que {@code liquiderDouane} décrémente à chaque
 * utilisation. La confusion de vocabulaire a produit la faille — le code disait exactement ce que
 * son auteur croyait.
 *
 * <p>Trois familles suffisent, parce qu'elles correspondent à trois régimes de protection
 * différents. Les nommer oblige l'appelant à dire ce qu'il touche, et c'est précisément ce que
 * personne ne se demandait.
 */
public enum ChampDossier {

    /**
     * Intitulé, dates, références, libellés : aucun effet financier.
     *
     * <p>Reste modifiable très tard, y compris par correction administrateur sur un dossier clos —
     * c'est l'intérêt même de l'override, et le lui retirer ne protégerait rien.
     */
    CONTENU_DECLARATIF,

    /**
     * Montants de référence : {@code montantCordon}, {@code montantTVAInterieure}, et les lignes du
     * récapitulatif qui les composent.
     *
     * <p>Ce sont des plafonds. Ils se figent à l'ouverture du crédit, parce que les soldes en
     * dérivent et que les utilisations s'y adossent.
     */
    MONTANT_DE_REFERENCE,

    /**
     * Soldes et quotas engagés : {@code soldeCordon}, {@code soldeTVA}, {@code tvaImportationDouane}.
     *
     * <p>Ceux-là n'appartiennent pas à la saisie mais à la consommation. Dès qu'une utilisation
     * existe, les réécrire efface une dépense réelle.
     */
    SOLDE_ENGAGE
}
