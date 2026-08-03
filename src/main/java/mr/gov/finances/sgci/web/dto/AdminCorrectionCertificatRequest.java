package mr.gov.finances.sgci.web.dto;

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
    private BigDecimal montantCordon;
    private BigDecimal montantTVAInterieure;
    private BigDecimal valeurDouaneFournitures;
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    private BigDecimal tvaImportationDouane;
    private BigDecimal montantMarcheHt;
    private BigDecimal tvaCollecteeTravaux;
}
