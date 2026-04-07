package mr.gov.finances.sgci.domain.enums;

/**
 * Cycle de vie d’une demande de transfert de crédit.
 */
public enum StatutTransfert {
    /** Demande créée ; pièces éventuellement en cours de dépôt. */
    DEMANDE,
    /**
     * Au moins une pièce obligatoire a été déposée (transition automatique depuis {@link #DEMANDE}).
     */
    EN_COURS,
    /**
     * Valeur historique / réservée : le workflow actuel ne la pose pas (validation exécute directement
     * le transfert vers {@link #TRANSFERE}). Conservée pour compatibilité base de données.
     */
    VALIDE,
    /** Transfert douane → intérieur effectué sur le certificat. */
    TRANSFERE,
    /** Demande refusée par DGTCP ou le Président. */
    REJETE
}
