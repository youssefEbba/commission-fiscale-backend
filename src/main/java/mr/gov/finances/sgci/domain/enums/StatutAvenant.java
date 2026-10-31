package mr.gov.finances.sgci.domain.enums;

/**
 * Le cycle de l'avenant, tel que l'équipe l'a décrit (octobre 2026) :
 * Déposé → En cours de traitement → Adopté → Appliqué, ou Rejeté.
 */
public enum StatutAvenant {
    /** Déposé par l'entreprise ou l'autorité contractante, avec ses pièces. */
    DEPOSE,
    /** Pris en charge par la DGTCP ; pour un avenant financier, aussi le temps de la correction. */
    EN_COURS_TRAITEMENT,
    /** Validé par le Président et, s'il est financier, sa demande de correction adoptée. */
    ADOPTE,
    /** Appliqué par la DGTCP : le crédit et ses soldes sont à jour. */
    APPLIQUE,
    REJETE
}
