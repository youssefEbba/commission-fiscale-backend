package mr.gov.finances.sgci.domain.enums;

public enum Role {
    ADMIN_SI,
    PRESIDENT,
    DGD,
    DGTCP,
    DGI,
    DGB,
    AUTORITE_CONTRACTANTE,
    AUTORITE_UPM,
    AUTORITE_UEP,
    ENTREPRISE,
    SOUS_TRAITANT,
    /** Support : agit au nom d’une entreprise ou d’une autorité contractante après impersonation (JWT enrichi). */
    COMMISSION_RELAIS
}
