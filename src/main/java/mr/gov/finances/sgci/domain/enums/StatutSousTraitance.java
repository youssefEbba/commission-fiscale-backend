package mr.gov.finances.sgci.domain.enums;

public enum StatutSousTraitance {
    DEMANDE,
    AUTORISEE,
    REFUSEE,
    /** Autorisation retirée par le titulaire ; nouvelle procédure requise pour rétablir un lien. */
    REVOQUEE,
    /** Mise en pause par le titulaire ; réactivable sans repasser par DGTCP. */
    SUSPENDUE,
    EN_COURS
}
