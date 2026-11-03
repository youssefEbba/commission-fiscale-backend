package mr.gov.finances.sgci.domain.enums;

/**
 * Nature du bailleur d'une convention : elle distingue les projets financés sur fonds nationaux de
 * ceux financés par des partenaires extérieurs (cahier des charges, point 4).
 */
public enum CategorieBailleur {
    /** État mauritanien, budget national, fonds propres d'un établissement public. */
    NATIONAL,
    /** Banque mondiale, BAD, BID, Union européenne… */
    MULTILATERAL,
    /** Coopération d'un État partenaire (agence de développement, fonds souverain). */
    BILATERAL,
    PRIVE,
    NON_RENSEIGNE
}
