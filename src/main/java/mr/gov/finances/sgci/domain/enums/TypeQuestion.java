package mr.gov.finances.sgci.domain.enums;

/** La forme de réponse attendue à une question d'enquête. */
public enum TypeQuestion {
    /** Une note de 1 à 5. */
    NOTE,
    /** Un commentaire libre. */
    TEXTE,
    /** Un choix parmi les options de la question. */
    CHOIX
}
