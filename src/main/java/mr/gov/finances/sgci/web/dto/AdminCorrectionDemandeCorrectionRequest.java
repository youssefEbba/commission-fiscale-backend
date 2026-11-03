package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Correction administrateur (ADMIN_SI) d'informations d'une demande de correction, en dehors du
 * workflow normal. Patch partiel : seuls les champs non nuls sont appliqués. Motif obligatoire,
 * transmis séparément et journalisé dans l'audit ({@link mr.gov.finances.sgci.domain.enums.AuditAction#ADMIN_CORRECTION}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCorrectionDemandeCorrectionRequest {

    @DecimalMin("0")
    private BigDecimal creditInterieur;
    @DecimalMin("0")
    private BigDecimal creditExterieur;
    @Size(max = 500)
    private String intituleMarche;
    @Size(max = 120)
    private String referenceAppelOffre;
}
