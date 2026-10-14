package mr.gov.finances.sgci.domain.enums;

/**
 * Les blocs de contenu qu'un état exporté peut contenir.
 *
 * <p>Énumération purement Java, jamais persistée : un bloc ajouté ne demande aucune migration.
 */
public enum BlocReporting {

    ENGAGEMENT,

    BLOCAGES,

    A_FERMER,

    PRODUCTION
}
