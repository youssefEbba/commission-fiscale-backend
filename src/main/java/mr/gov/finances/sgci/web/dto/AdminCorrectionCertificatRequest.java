package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Correction administrateur (ADMIN_SI) d'informations d'un certificat de crédit, à tout moment
 * (y compris après ouverture). Patch partiel : seuls les champs non nuls sont appliqués. Motif
 * obligatoire, transmis séparément et journalisé dans l'audit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCorrectionCertificatRequest {

    private Instant dateValidite;
    @DecimalMin("0")
    private BigDecimal montantCordon;
    @DecimalMin("0")
    private BigDecimal montantTVAInterieure;
    @DecimalMin("0")
    private BigDecimal valeurDouaneFournitures;
    @DecimalMin("0")
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    /** (c) Taxes de consommation — part du crédit douanier hors TVA, facultative. */
    @DecimalMin("0")
    private BigDecimal taxesConsommation;
    @DecimalMin("0")
    private BigDecimal tvaImportationDouane;
    @DecimalMin("0")
    private BigDecimal montantMarcheHt;
    @DecimalMin("0")
    private BigDecimal tvaCollecteeTravaux;
}
