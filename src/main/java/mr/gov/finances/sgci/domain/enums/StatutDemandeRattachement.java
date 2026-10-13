package mr.gov.finances.sgci.domain.enums;

/**
 * Cycle de vie d'une demande de rattachement d'une personne à une entreprise ou à une autorité
 * contractante.
 *
 * <p>Trois états seulement, comme {@link StatutDemandeResetPassword} : une demande est en attente du
 * Président, approuvée, ou refusée avec motif. Rien n'est réversible — une demande refusée n'est pas
 * rouverte, le demandeur en dépose une nouvelle, ce qui conserve la trace de chaque tentative.
 */
public enum StatutDemandeRattachement {
    EN_ATTENTE,
    APPROUVEE,
    REFUSEE
}
