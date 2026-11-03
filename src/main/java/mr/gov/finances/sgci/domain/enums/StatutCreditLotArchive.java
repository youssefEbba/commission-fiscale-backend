package mr.gov.finances.sgci.domain.enums;

/** Avancement d'un crédit dans un lot de reprise d'archive. */
public enum StatutCreditLotArchive {
    /** Il manque l'entreprise, l'autorité, la convention ou le marché, ou une anomalie reste à confirmer. */
    A_COMPLETER,
    /** Tout est choisi : le crédit peut être importé. */
    PRET,
    IMPORTE,
    /** L'import a échoué ; le message est gardé et le crédit peut être repris. */
    ERREUR,
    /** Écarté par l'opérateur : il n'est pas importé. */
    IGNORE
}
