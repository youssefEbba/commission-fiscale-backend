package mr.gov.finances.sgci.domain.enums;

/**
 * Nature de l'empreinte apposée sur les documents officiels.
 *
 * <p>Les deux natures partagent la même table, la même validation PNG et le même versionnement :
 * seule la clé fonctionnelle change, qui devient {@code (type, role, utilisateur)}.
 */
public enum TypeEmpreinte {
    /** Signature manuscrite du titulaire, propre à la personne. */
    SIGNATURE,
    /** Cachet ou sceau. Personnel, ou institutionnel lorsque {@code utilisateur} est nul. */
    CACHET
}
