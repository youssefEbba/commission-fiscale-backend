package mr.gov.finances.sgci.web.dto;

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

    private BigDecimal creditInterieur;
    private BigDecimal creditExterieur;
    private String intituleMarche;
}
