package mr.gov.finances.sgci.domain.enums;

public enum StatutCertificat {
    /** Brouillon : mise en place non finalisée. */
    BROUILLON,
    /** Demande envoyée : en attente de prise en charge par un acteur (DGI / DGD / DGTCP). */
    ENVOYEE,
    EN_CONTROLE,
    INCOMPLETE,
    A_RECONTROLER,
    EN_VALIDATION_PRESIDENT,
    VALIDE_PRESIDENT,
    EN_OUVERTURE_DGTCP,
    OUVERT,
    MODIFIE,
    CLOTURE,
    ANNULE
}
