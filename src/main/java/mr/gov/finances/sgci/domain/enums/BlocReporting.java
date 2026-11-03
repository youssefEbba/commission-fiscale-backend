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

    PRODUCTION,

    /** Indicateurs d'impact de la digitalisation : volumes, délais, taux, montants, avant / après. */
    IMPACT,

    /** Bénéficiaires : nature, catégorie, secteur, financement, évolution par année fiscale. */
    BENEFICIAIRES,

    /** Satisfaction des usagers, réservée aux titulaires de {@code feedback.dashboard.view}. */
    SATISFACTION
}
