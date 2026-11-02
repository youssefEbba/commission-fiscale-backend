package mr.gov.finances.sgci.domain.enums;

/**
 * Le cycle d'une enquête de satisfaction. Publiée, elle n'est ouverte aux réponses qu'entre ses
 * dates de début et de fin : l'ouverture se lit sur les dates, sans tâche planifiée.
 */
public enum StatutEnquete {
    BROUILLON,
    PUBLIEE,
    CLOTUREE
}
