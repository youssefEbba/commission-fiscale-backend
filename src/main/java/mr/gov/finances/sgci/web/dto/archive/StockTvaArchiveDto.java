package mr.gov.finances.sgci.web.dto.archive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une entrée du stock de TVA déductible reconstituée en rejouant le relevé selon les règles de
 * l'application : la TVA payée à une importation, ou la TVA d'importation transférée.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTvaArchiveDto {
    /** {@code UTILISATION_DOUANE} ou {@code TRANSFERT_CREDIT}, comme {@code TvaDeductibleStockSource}. */
    private String source;
    /** Rang de l'importation dans {@code ReleveArchiveDto.utilisations} ; null pour le transfert. */
    private Integer indexUtilisation;
    private Instant date;
    private BigDecimal montantInitial;
    /** Ce qui reste après les déductions des utilisations intérieures rejouées. */
    private BigDecimal montantRestant;
}
