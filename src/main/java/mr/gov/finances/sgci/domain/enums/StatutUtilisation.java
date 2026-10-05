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
    /**
     * La DGTCP a contrôlé le dossier — chèque et bulletin visé — et l'a transmis au Président.
     *
     * <p>Nommé comme {@link #ENVOYEE_AU_TRESOR}, son jumeau structurel : un statut est lu par celui
     * qui doit agir ensuite, et celui-ci dit au Président que la main est à lui.
     */
    TRANSMISE_AU_PRESIDENT,
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

    /**
     * Le Président a émis le certificat d'utilisation (numérotation {@code CU-nnn/AAAA}).
     *
     * <p>Acte strictement présidentiel, placé avant l'étape de paiement : le certificat est la
     * pièce présentée au Trésor en douane, à la DGI en TVA intérieure. Il suit
     * {@link #TRANSMISE_AU_PRESIDENT} en douane, {@link #VALIDEE} en TVA intérieure.
     */
    CERTIFICAT_EMIS,
    REJETEE,

    /**
     * Clôture administrative (ex. après transfert (d) → intérieur) : plus de suite pour cette demande.
     */
    CLOTUREE
}
