package mr.gov.finances.sgci.domain.enums;

/**
 * Résultat agrégé pour l'écran de scan / vérification d'un certificat (code-barres = {@code numero}).
 */
public enum EtatVerificationCertificat {
    /** Aucun certificat ne correspond au numéro scanné. */
    INCONNU,
    /** Certificat ouvert ou modifié, non expiré — utilisable (sous réserve des soldes). */
    VALIDE,
    /** Certificat ouvert/modifié mais date de validité dépassée. */
    EXPIRE,
    /** Certificat clôturé définitivement. */
    CLOTURE,
    /** Certificat annulé. */
    ANNULE,
    /** Dossier de mise en place encore en cours (pas encore OUVERT). */
    EN_COURS,
    /** Autre situation non utilisable (ex. incomplet sans être annulé). */
    NON_VALIDE
}
