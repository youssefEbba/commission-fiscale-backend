package mr.gov.finances.sgci.web.dto.archive;

import lombok.Data;

import java.math.BigDecimal;

/** Une ligne de la ventilation fiscale d'une utilisation douanière. */
@Data
public class LigneTaxeArchiveDto {

    /** Code court de la taxe : DD, TVA, RS, PC, PSC, TCO, TT, TTI, RIF, IMF. */
    private String codeTaxe;

    /** Libellé complet, repris du référentiel des taxes lorsqu'il y figure. */
    private String denominationTaxe;

    private BigDecimal valeur;

    /** {@code AU_CI} (imputée sur le crédit) ou {@code A_PAYER} (réglée comptant). */
    private String affectation;
}
