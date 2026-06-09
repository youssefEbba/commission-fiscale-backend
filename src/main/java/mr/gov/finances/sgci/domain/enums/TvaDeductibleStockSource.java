package mr.gov.finances.sgci.domain.enums;

/**
 * Origine d'une entrée du stock TVA déductible (FIFO sur le certificat).
 */
public enum TvaDeductibleStockSource {
    /** Créé lors de la liquidation douanière d'une utilisation (TVA AU_CI). */
    UTILISATION_DOUANE,
    /** Créé lors du transfert du reliquat TVA importation douane vers le stock intérieur. */
    TRANSFERT_CREDIT
}
