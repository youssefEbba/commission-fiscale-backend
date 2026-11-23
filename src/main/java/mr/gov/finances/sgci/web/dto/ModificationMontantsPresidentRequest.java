package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Montants corrigés par le Président avant l'émission du certificat d'utilisation
 * ({@code PATCH /api/utilisations-credit/{id}/montants-president}). Un champ absent garde sa valeur.
 *
 * <p>Douane : {@code droitsImputes} et {@code tvaImputee}, que le Président ne peut que réduire (le
 * chèque de la part à payer est déjà encaissé). TVA intérieure : {@code montantTVA} (TVA collectée)
 * et {@code tvaDeductibleUtilisee}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModificationMontantsPresidentRequest {

    /** Douane : droits et taxes hors TVA imputés sur le crédit. */
    @DecimalMin("0")
    private BigDecimal droitsImputes;

    /** Douane : TVA d'importation imputée sur le crédit. */
    @DecimalMin("0")
    private BigDecimal tvaImputee;

    /** TVA intérieure : TVA collectée. */
    @DecimalMin("0")
    private BigDecimal montantTVA;

    /** TVA intérieure : TVA déductible imputée. */
    @DecimalMin("0")
    private BigDecimal tvaDeductibleUtilisee;

    @NotBlank
    @Size(max = 500)
    private String motif;
}
