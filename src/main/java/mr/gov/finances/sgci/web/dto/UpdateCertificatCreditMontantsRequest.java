package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCertificatCreditMontantsRequest {

    @NotNull(message = "Le montant cordon est obligatoire")
    private BigDecimal montantCordon;

    @NotNull(message = "Le montant TVA intérieure est obligatoire")
    private BigDecimal montantTVAInterieure;

    /** Récap. fiscal optionnel — mêmes lignes que {@link CreateCertificatCreditRequest}. */
    private BigDecimal valeurDouaneFournitures;
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    private BigDecimal tvaImportationDouane;
    private BigDecimal montantMarcheHt;
    private BigDecimal tvaCollecteeTravaux;
}
