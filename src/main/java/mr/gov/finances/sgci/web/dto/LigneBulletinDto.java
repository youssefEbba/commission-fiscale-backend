package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;

import java.math.BigDecimal;

/** Représentation d'une ligne du bulletin de liquidation douanière. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneBulletinDto {

    private Long id;

    /** Code court : "DD", "TVA", "RS", "PSC", "IMF", "PC", "TSI"… */
    private String codeTaxe;

    /** Libellé complet de la taxe. */
    private String denominationTaxe;

    /** GLOBALE ou ARTICLE */
    private TypeLigneTaxe typeLigne;

    /** Valeur saisie par l'entreprise (MRU). */
    private BigDecimal valeurTaxe;

    /**
     * Décision DGD : AU_CI (pris en charge par le CI) ou A_PAYER (comptant).
     * Null tant que la liquidation n'a pas été effectuée.
     */
    private AffectationTaxe affectation;
}
