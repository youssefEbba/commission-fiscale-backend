package mr.gov.finances.sgci.domain.enums;

public enum StatutReclamationCorrection {
    SOUMISE,
    ACCEPTEE,
    REJETEE,
    /** Retrait par le déposant (ou AC) avant traitement DGTCP : le statut de la demande et les visas restent inchangés. */
    ANNULEE
}
