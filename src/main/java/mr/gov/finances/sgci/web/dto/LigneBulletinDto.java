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

    /** Proposition entreprise (AU_CI / A_PAYER). */
    private AffectationTaxe affectationEntreprise;

    /**
     * Décision finale DGD : AU_CI ou A_PAYER. Null avant visa DGD.
     * Si différent de {@link #affectationEntreprise}, le DGD a modifié la proposition.
     */
    private AffectationTaxe affectation;

    /** {@code true} si le DGD a modifié l'affectation proposée par l'entreprise. */
    private Boolean affectationModifieeParDgd;
}
