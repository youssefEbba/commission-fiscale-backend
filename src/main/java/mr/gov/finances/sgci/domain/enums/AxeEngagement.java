package mr.gov.finances.sgci.domain.enums;

/**
 * Les axes selon lesquels l'engagement de l'État peut etre ventile.
 *
 * <p>Énumération <b>purement Java</b> : elle n'est jamais persistée, donc elle ne peut pas
 * introduire de colonne ENUM MySQL, et ajouter un axe ne demande aucune migration.
 *
 * <p>Le ministère de tutelle et le secteur d'activité en sont volontairement absents :
 * {@code AutoriteContractante.ministereTutelleNom} et {@code Entreprise.activite} sont du texte
 * libre, et un regroupement dessus presenterait des doublons orthographiques comme autant
 * d'entites distinctes. Ils attendent un referentiel.
 */
public enum AxeEngagement {

    /** L'autorité contractante titulaire du dossier d'origine. */
    AUTORITE_CONTRACTANTE,

    /** Le bailleur, atteint par la convention du dossier. Maillon nullable. */
    BAILLEUR,

    /** La convention sous laquelle le dossier a été instruit. */
    CONVENTION,

    /** L'entreprise titulaire du certificat. Rattachement toujours renseigné. */
    ENTREPRISE
}
