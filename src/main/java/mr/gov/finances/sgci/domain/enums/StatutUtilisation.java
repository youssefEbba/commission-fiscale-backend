package mr.gov.finances.sgci.domain.enums;

public enum StatutUtilisation {
    /** Brouillon : non soumis aux services. */
    BROUILLON,
    DEMANDEE,
    INCOMPLETE,
    A_RECONTROLER,
    EN_VERIFICATION,
    VISE,
    VALIDEE,
    LIQUIDEE,
    APUREE,
    REJETEE,

    /**
     * Clôture administrative (ex. après transfert (d) → intérieur) : plus de suite pour cette demande.
     */
    CLOTUREE
}
