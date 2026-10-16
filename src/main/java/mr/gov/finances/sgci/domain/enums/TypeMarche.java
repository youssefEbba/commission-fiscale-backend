package mr.gov.finances.sgci.domain.enums;

/**
 * La nature de la prestation qu'un marché couvre.
 *
 * <p>Demandé par la Commission pour distinguer ce qui est acheté : des biens, un service, ou des
 * travaux. L'instruction fiscale n'est pas la même selon le cas — une fourniture importée touche le
 * cordon douanier là où une prestation locale relève de la TVA intérieure — et le reporting pourra
 * ensuite ventiler là-dessus.
 *
 * <p><b>Sa colonne est convertie en VARCHAR au démarrage</b>, par le registre de
 * {@code ColonnesTexteSchemaMigration}. Sans cette conversion, Hibernate la rendrait en colonne
 * MySQL {@code enum(…)} native, qu'un redémarrage n'élargit jamais : le jour où un quatrième type
 * apparaît, l'écriture casserait en production sans qu'aucun test ne le voie, puisque H2 recrée son
 * schéma à chaque exécution.
 */
public enum TypeMarche {

    /** Achat de biens. */
    FOURNITURE,

    /** Prestation de services. */
    PRESTATION,

    /** Travaux. */
    TRAVAUX
}
