package mr.gov.finances.sgci.domain.enums;

public enum StatutUtilisation {
    /** Brouillon : non soumis aux services. */
    BROUILLON,
    DEMANDEE,
    INCOMPLETE,
    A_RECONTROLER,
    EN_VERIFICATION,
    VISE,
    /** DGD a renseigné les montants AU_CI / A_PAYER sur les lignes du bulletin. */
    EN_CONTROLE_DGD,
    /** Entreprise a fourni le chèque certifié (banque, N°, montant). */
    CHEQUE_SAISI,
    /** DGTCP a validé le chèque et envoyé la demande au Trésor. */
    ENVOYEE_AU_TRESOR,
    /** DGTCP a saisi les quittances Trésor — débit financier imminent. */
    QUITTANCES_ENREGISTREES,
    VALIDEE,
    /**
     * TVA intérieure : la DGI a déposé la quittance attestant le paiement. Étape obligatoire entre
     * la validation DGTCP et l'apurement — sans elle, le solde de TVA ne peut pas être mouvementé.
     */
    QUITTANCE_DGI_ENREGISTREE,
    LIQUIDEE,
    APUREE,
    REJETEE,

    /**
     * Clôture administrative (ex. après transfert (d) → intérieur) : plus de suite pour cette demande.
     */
    CLOTUREE
}
