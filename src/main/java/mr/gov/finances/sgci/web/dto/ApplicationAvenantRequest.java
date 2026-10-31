package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Le nouveau récapitulatif du crédit, saisi par la DGTCP à l'application d'un avenant financier.
 * Mêmes lignes qu'à la mise en place ; le front les pré-remplit avec les crédits retenus par la
 * correction adoptée. Sans objet pour un avenant non financier.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationAvenantRequest {
    /** a */
    private BigDecimal valeurDouaneFournitures;
    /** b */
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    /** c */
    private BigDecimal taxesConsommation;
    /** d accordé */
    private BigDecimal tvaImportationDouaneAccordee;
    /** e = b + c + d */
    private BigDecimal montantCordon;
    /** f */
    private BigDecimal montantMarcheHt;
    /** g */
    private BigDecimal tvaCollecteeTravaux;
    /** h = g − d */
    private BigDecimal montantTVAInterieure;
}
