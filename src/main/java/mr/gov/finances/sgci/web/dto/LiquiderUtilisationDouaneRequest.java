package mr.gov.finances.sgci.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;

import java.math.BigDecimal;
import java.util.List;

/**
 * Requête de liquidation douanière envoyée par le DGD.
 * <p>
 * Le DGD affecte chaque ligne du bulletin à AU_CI (pris en charge par le crédit d'impôt)
 * ou A_PAYER (paiement comptant par l'entreprise).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiquiderUtilisationDouaneRequest {

    /**
     * Décisions du DGD, une entrée par ligne du bulletin.
     * Toutes les lignes de la demande doivent être couvertes.
     */
    @NotNull(message = "La liste des décisions par ligne est obligatoire")
    @Valid
    private List<DecisionLigneRequest> decisions;

    /** Décision du DGD pour une ligne du bulletin. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionLigneRequest {

        /** Id de la {@link mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation}. */
        @NotNull(message = "L'id de la ligne est obligatoire")
        private Long ligneId;

        /** AU_CI = pris en charge par le CI ; A_PAYER = paiement comptant. */
        @NotNull(message = "L'affectation (AU_CI ou A_PAYER) est obligatoire")
        private AffectationTaxe affectation;

        /**
         * Valeur corrigée par le DGD (optionnelle).
         * Si renseignée, remplace la valeur saisie par l'entreprise sur la ligne.
         * Utile si le DGD rectifie un montant lors de son annotation.
         */
        private BigDecimal valeurTaxe;
    }
}
